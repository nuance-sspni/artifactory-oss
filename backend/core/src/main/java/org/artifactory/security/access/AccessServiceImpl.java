package org.artifactory.security.access;

import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.artifactory.addon.AddonsManager;
import org.artifactory.addon.ha.HaCommonAddon;
import org.artifactory.api.jackson.JacksonReader;
import org.artifactory.api.security.AuthorizationException;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.api.security.SecurityService;
import org.artifactory.api.security.UserGroupService;
import org.artifactory.api.security.access.*;
import org.artifactory.common.ArtifactoryHome;
import org.artifactory.common.ConstantValues;
import org.artifactory.config.InternalCentralConfigService;
import org.artifactory.descriptor.config.CentralConfigDescriptor;
import org.artifactory.descriptor.security.SecurityDescriptor;
import org.artifactory.descriptor.security.accesstoken.AccessClientSettings;
import org.artifactory.sapi.common.ExportSettings;
import org.artifactory.sapi.common.ImportSettings;
import org.artifactory.security.UserGroupInfo;
import org.artifactory.security.UserInfo;
import org.artifactory.spring.ContextReadinessListener;
import org.artifactory.spring.Reloadable;
import org.artifactory.storage.security.service.UserGroupStoreService;
import org.artifactory.version.CompoundVersionDetails;
import org.jfrog.access.client.*;
import org.jfrog.access.client.http.RestRequest;
import org.jfrog.access.client.http.RestResponse;
import org.jfrog.access.client.token.TokenRequest;
import org.jfrog.access.client.token.TokenResponse;
import org.jfrog.access.client.token.TokenVerifyResult;
import org.jfrog.access.client.token.TokensInfoResponse;
import org.jfrog.access.common.ServiceId;
import org.jfrog.access.common.SubjectFQN;
import org.jfrog.access.token.JwtAccessToken;
import org.jfrog.security.util.ULID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.Thread.sleep;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.artifactory.security.access.AccessServiceConstants.ARTIFACTORY_SERVICE_ANY_ID_PATTERN;
import static org.artifactory.security.access.AccessServiceConstants.ARTIFACTORY_SERVICE_TYPE;
import static org.artifactory.security.access.ArtifactoryAdminScopeToken.SCOPE_ARTIFACTORY_ADMIN_PATTERN;
import static org.artifactory.security.access.MemberOfGroupsScopeToken.SCOPE_MEMBER_OF_GROUPS_PATTERN;
import static org.jfrog.access.token.JwtAccessToken.SCOPE_DELIMITER;

/**
 * @author Yinon Avraham
 */
@Service
@Reloadable(beanClass = AccessService.class, initAfter = InternalCentralConfigService.class)
public class AccessServiceImpl implements AccessService, ContextReadinessListener {

    private static final Logger log = LoggerFactory.getLogger(AccessServiceImpl.class);
    private static final int MIN_INSTANCE_ID_LENGTH = 20;

    //Accepted scopes:
    private static final String SCOPE_API = "api:*";
    private static final Pattern SCOPE_API_PATTERN = Pattern.compile(Pattern.quote(SCOPE_API));

    private final List<Pattern> acceptedScopePatterns = Lists.newArrayList(asList(
            SCOPE_API_PATTERN, SCOPE_MEMBER_OF_GROUPS_PATTERN, SCOPE_ARTIFACTORY_ADMIN_PATTERN));

    @Autowired
    private AddonsManager addonsManager;

    @Autowired
    private UserGroupStoreService userGroupStore;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private InternalCentralConfigService centralConfigService;

    @Autowired
    private UserGroupService userGroupService;

    private final ContextStateDependantActionRunner contextStateDependantActionRunner = new ContextStateDependantActionRunner();
    private ArtifactoryHome artifactoryHome;
    private ServiceId serviceId;
    private AccessClient accessClient;
    private ArtifactoryAccessClientConfigStore configStore;

    SecurityService securityService() {
        return securityService;
    }

    InternalCentralConfigService centralConfigService() {
        return centralConfigService;
    }

    ArtifactoryHome artifactoryHome() {
        return artifactoryHome;
    }

    @Override
    public void registerAcceptedScopePattern(@Nonnull Pattern pattern) {
        synchronized (acceptedScopePatterns) {
            log.debug("Registering accepted scope pattern: {}", requireNonNull(pattern, "pattern is required"));
            if (!acceptedScopePatterns.stream().anyMatch(p -> p.pattern().equals(pattern.pattern()))) {
                acceptedScopePatterns.add(pattern);
            } else {
                log.debug("Pattern already exists in the accepted scope patterns: '{}'", pattern);
            }
        }
    }

    @Nonnull
    @Override
    public List<TokenInfo> getTokenInfos() {
        try {
            TokensInfoResponse tokensInfoResponse = accessClient.token().getTokensInfo();
            List<TokenInfo> result = tokensInfoResponse.getTokens().stream()
                    .map(this::toTokenInfo)
                    .filter(this::isNonInternalToken)
                    .collect(Collectors.toList());
            return result;
        } catch (AccessClientException e) {
            throw new RuntimeException("Failed to get tokens information.", e);
        }
    }

    @Override
    public AccessClient getAccessClient() {
        return accessClient;
    }

    @Override
    public void encryptOrDecrypt(boolean encrypt) {
        configStore.encryptOrDecryptAccessCreds(encrypt);
    }

    @Override
    public File createBootstrapBundle() {
        RestRequest request = RestRequest.post("/api/v1/system/bootstrap_bundle").build();
        RestResponse response = accessClient.useAuth(accessAdminCredentials()).restCall(request);
        if (response.isSuccessful()) {
            try {
                Map responseData = JacksonReader.bytesAsClass(response.getBody(), Map.class);
                String filepath = (String) responseData.get("file");
                File file = new File(filepath);
                if (file.exists()) {
                    return file;
                }
                throw new IllegalStateException("Created bootstrap bundle file does not exist: " + filepath);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            log.error("Failed to create bootstrap bundle. Response from access server: {}\n{}",
                        response.getStatusCode(), response.getBodyAsString());
            throw new RuntimeException("Failed to create bootstrap bundle");
        }
    }

    private TokenInfo toTokenInfo(org.jfrog.access.client.token.TokenInfo clientTokenInfo) {
        return new TokenInfoImpl(clientTokenInfo.getTokenId(),
                clientTokenInfo.getIssuer(),
                clientTokenInfo.getSubject(),
                clientTokenInfo.getExpiry(),
                clientTokenInfo.getIssuedAt(),
                clientTokenInfo.isRefreshable());
    }

    private boolean isNonInternalToken(TokenInfo tokenInfo) {
        try {
            SubjectFQN subject = SubjectFQN.fromFullyQualifiedName(tokenInfo.getSubject());
            if (subject.getServiceId().equals(getArtifactoryServiceId())) {
                if (!UserTokenSpec.isUserToken(tokenInfo)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void init() {
        artifactoryHome = ArtifactoryHome.get();
        initServiceId();
        initIfNeeded();
    }

    private void initIfNeeded() {
        if (accessClient == null) {
            initAccessService(true);
        }
    }

    private void initAccessService(boolean doBootstrap) {
        configStore = new ArtifactoryAccessClientConfigStore(this, serviceId);
        IOUtils.closeQuietly(accessClient);
        accessClient = configStore.newClientBuilder().create();
        long secondsToWait = ConstantValues.accessClientWaitForServer.getLong(artifactoryHome);
        waitForAccessServer(TimeUnit.SECONDS.toMillis(secondsToWait));
        if (doBootstrap) {
            AccessClientBootstrap bootstrap = new AccessClientBootstrap(configStore, accessClient);
            accessClient = bootstrap.getAccessClient(); //access client can be updated with the admin token
        }
    }

    private void waitForAccessServer(long timeoutMillis) {
        log.info("Waiting for access server...");
        long startTime = System.currentTimeMillis();
        long now = startTime;
        boolean success = false;
        AccessClient accessClientNoAuth = this.accessClient.useAuth(null);
        while (now - startTime < timeoutMillis) {
            try {
                log.debug("Pinging access server...");
                success = accessClientNoAuth.ping();
                if (success) {
                    log.info("Got response from Access server after {} ms, continuing.",
                            System.currentTimeMillis() - startTime);
                    break;
                }
                log.debug("Pinging access server did nor succeed, waiting for 500ms before retrying...");
                sleep(500);
            } catch (AccessClientException e) {
                log.debug("Could not ping access server: {}", e.toString());
                //ignore - assuming 404 or 503 and such. In the end, if ping is not successful then it will anyway fail.
            } catch (InterruptedException e) {
                throw new RuntimeException("Waiting for access server got interrupted.", e);
            }
            now = System.currentTimeMillis();
        }
        if (!success) {
            throw new IllegalStateException("Waiting for access server to respond timed-out after " +
                    (System.currentTimeMillis() - startTime) + " milliseconds.");
        }
    }

    private void initServiceId() {
        File serviceIdFile = new File(artifactoryHome.getAccessClientDir(), "keys/service_id");
        try {
            if (serviceIdFile.exists()) {
                serviceId = ServiceId.fromFormattedName(Files.readAllLines(serviceIdFile.toPath()).get(0));
            } else {
                String instanceId = getServiceInstanceID();
                serviceId = new ServiceId(ARTIFACTORY_SERVICE_TYPE, instanceId);
                FileUtils.forceMkdir(serviceIdFile.getParentFile());
                Files.write(serviceIdFile.toPath(), serviceId.toString().getBytes(), WRITE, CREATE);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize the service ID.", e);
        }
    }

    private String getServiceInstanceID() {
        HaCommonAddon haCommonAddon = addonsManager.addonByType(HaCommonAddon.class);
        String id;
        if (haCommonAddon.isHaConfigured()) {
            id = artifactoryHome.getClusterId();
        } else {
            id = ULID.random().toLowerCase();
        }
        return normalizeInstanceId(id);
    }

    static String normalizeInstanceId(String id) {
        Matcher matcher = ServiceId.ELEMENT_PATTERN.matcher(id);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            //Fill the gap (if any)
            for (int i = builder.length(); i < matcher.start(); i++) {
                builder.append("_");
            }
            builder.append(matcher.group());
        }
        String normalizedId = builder.toString();
        if (normalizedId.length() < MIN_INSTANCE_ID_LENGTH) {
            throw new IllegalArgumentException("Instance ID is too short. (normalized='" + normalizedId +
                    "', original='" + id + "')");
        }
        return normalizedId;
    }

    @Override
    @Nonnull
    public CreatedTokenInfo createToken(@Nonnull TokenSpec tokenSpec) {
        String subject = null;
        try {
            subject = tokenSpec.createSubject(serviceId).toString();
            List<String> scope = getEffectiveScope(tokenSpec);
            List<String> audience = getEffectiveAudience(tokenSpec);
            TokenRequest tokenRequest = new TokenRequest(scope, tokenSpec.isRefreshable(), subject,
                    tokenSpec.getExpiresIn(), null, audience);
            assertLoggedInCanCreateToken(tokenRequest);
            TokenResponse tokenResponse = accessClient.token().create(tokenRequest);
            return toCreatedTokenInfo(tokenResponse, scope);
        } catch (AccessClientException e) {
            log.error("Failed to create token for subject '{}': {}", subject, e.getMessage());
            log.debug("Failed to create token for subject '{}'", subject, e);
            throw new RuntimeException("Failed to create token for subject '" + subject + "'.", e);
        }
    }

    private void assertLoggedInCanCreateToken(TokenRequest tokenRequest) {
        String currentUsername = authorizationService.currentUsername();
        if (authorizationService.isAdmin() || SecurityService.USER_SYSTEM.equals(currentUsername)) {
            assertAdminUserCanCreateToken(tokenRequest);
        } else {
            assertNonAdminUserCanCreateToken(tokenRequest, currentUsername);
        }
        assertAllAudienceAreArtifactoryInstances(tokenRequest);
    }

    private void assertAllAudienceAreArtifactoryInstances(TokenRequest tokenRequest) {
        Optional<String> illegalAudience = tokenRequest.getAudience().stream()
                .filter(aud -> !ARTIFACTORY_SERVICE_ANY_ID_PATTERN.matcher(aud).matches())
                .findFirst();
        if (illegalAudience.isPresent()) {
            throw new AuthorizationException("Illegal audience: " + illegalAudience.get() + ", audience can contain " +
                    "only service IDs of Artifactory servers.");
        }
    }

    private void assertNonAdminUserCanCreateToken(TokenRequest tokenRequest, String currentUsername) {
        //non-admin users can only create tokens for themselves under this artifactory service ID
        if (!UserTokenSpec.isUserTokenSubject(tokenRequest.getSubject())) {
            throw new AuthorizationException("User " + currentUsername + " can only create user token for himself " +
                    "(requested: " + tokenRequest.getSubject() + ")");
        }
        String subjectUsername = UserTokenSpec.extractUsername(tokenRequest.getSubject());
        if (!currentUsername.equals(subjectUsername)) {
            throw new AuthorizationException("User " + currentUsername + " can only create user token for himself " +
                    "(requested: " + subjectUsername + ")");
        }
        ServiceId subjectServiceId = SubjectFQN.fromFullyQualifiedName(tokenRequest.getSubject()).getServiceId();
        if (!serviceId.equals(subjectServiceId)) {
            throw new AuthorizationException("User " + currentUsername + " can only create user token for himself " +
                    "under this Artifactory service ID (requested: " + tokenRequest.getSubject() + ")");
        }
        assertValidScopeForNonAdmin(tokenRequest.getScope());
        //non-admin users can have limited expires in
        assertValidExpiresInForNonAdmin(tokenRequest, currentUsername);
    }

    private void assertAdminUserCanCreateToken(TokenRequest tokenRequest) {
        tokenRequest.getScope().forEach(scopeToken -> {
            if (ArtifactoryAdminScopeToken.accepts(scopeToken)) {
                ServiceId serviceId = ArtifactoryAdminScopeToken.parse(scopeToken).getServiceId();
                if (!serviceId.equals(getArtifactoryServiceId())) {
                    throw new AuthorizationException("Admin can create token with admin privileges only on this " +
                            "Artifactory instance: " + getArtifactoryServiceId() +
                            " (requested: " + serviceId + ")");
                }
            }
        });
    }

    private void assertValidScopeForNonAdmin(List<String> scope) {
        Optional<String> unsupportedScopeToken = scope.stream()
                .filter(scopeToken ->
                        !SCOPE_API.equals(scopeToken) &&
                                !SCOPE_MEMBER_OF_GROUPS_PATTERN.matcher(scopeToken).matches())
                .findFirst();
        if (unsupportedScopeToken.isPresent()) {
            throw new AuthorizationException("Logged in user cannot request token with scope: " +
                    unsupportedScopeToken.get());
        }
        //for non-admin users - Check user is a member of requested groups
        Set<String> requestedGroupNames = collectGroupNamesFromScope(scope);
        // In case the token scope contains member-of-groups:* there is no need to verify groups
        if (requestedGroupNames.size() == 1 && requestedGroupNames.contains("*")) {
            return;
        }
        UserInfo userInfo = userGroupService.currentUser();
        Set<UserGroupInfo> userGroups = userInfo.getGroups();
        Set<String> acceptedGroupNames = Optional.ofNullable(userGroups)
                .map(groups -> groups.stream()
                        .map(UserGroupInfo::getGroupName)
                        .collect(Collectors.toSet()))
                .orElse(Collections.emptySet());
        requestedGroupNames.removeAll(acceptedGroupNames);
        if (!requestedGroupNames.isEmpty()) {
            throw new AuthorizationException("Logged in user is not a member of the following groups: " +
                    String.join(",", requestedGroupNames));
        }
    }

    void assertValidExpiresInForNonAdmin(TokenRequest tokenRequest, String currentUsername) {
        SecurityDescriptor securityDescriptor = centralConfigService.getDescriptor().getSecurity();
        AccessClientSettings accessClientSettings = securityDescriptor.getAccessClientSettings();
        long maxExpiresInMinutes = Optional.ofNullable(accessClientSettings)
                .map(settings -> Optional.ofNullable(settings.getUserTokenMaxExpiresInMinutes()))
                .orElse(Optional.empty())
                .orElse(AccessClientSettings.USER_TOKEN_MAX_EXPIRES_IN_MINUTES_DEFAULT);
        if (maxExpiresInMinutes != AccessClientSettings.USER_TOKEN_MAX_EXPIRES_IN_MINUTES_UNLIMITED) {
            long maxExpiresInSecs = maxExpiresInMinutes * 60;
            long expiresIn = Optional.ofNullable(tokenRequest.getExpiresIn()).orElse(Long.MAX_VALUE);
            if (expiresIn > maxExpiresInSecs) {
                throw new AuthorizationException("User " + currentUsername + " can only create user token with max " +
                        "expires in " + maxExpiresInSecs + " (requested: " + tokenRequest.getExpiresIn() + ")");
            }
        }
    }

    @Nonnull
    @Override
    public CreatedTokenInfo refreshToken(@Nonnull TokenSpec tokenSpec, @Nonnull String tokenValue,
            @Nonnull String refreshToken) {
        JwtAccessToken accessToken = parseToken(tokenValue);
        assertTokenCreatedByThisService(accessToken);
        try {
            List<String> scope = isNotEmpty(tokenSpec.getScope()) ? tokenSpec.getScope() : accessToken.getScope();
            //false if specified, true otherwise (a refreshable token keeps to be refreshable, unless specified otherwise)
            boolean refreshable = Boolean.FALSE.equals(tokenSpec.getRefreshable());
            List<String> audience =
                    isNotEmpty(tokenSpec.getAudience()) ? tokenSpec.getAudience() : accessToken.getAudience();
            TokenRequest tokenRequest = new TokenRequest(scope, refreshable, accessToken.getSubject(),
                    tokenSpec.getExpiresIn(), null, audience);
            TokenResponse tokenResponse = accessClient.token().refresh(refreshToken, tokenRequest);
            return toCreatedTokenInfo(tokenResponse, scope);
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == 404) {
                throw new TokenNotFoundException("Invalid access token or refresh token", e);
            } else if (e.getStatusCode() == 403) {
                throw new AuthorizationException("Refresh token operation rejected", e);
            }
            throw new RuntimeException("Access server refused to refresh the token", e);
        } catch (AccessClientException e) {
            log.error("Failed to refresh token for subject '{}': {}", accessToken.getSubject(), e.getMessage());
            log.debug("Failed to refresh token with id '{}' for subject '{}'",
                    accessToken.getTokenId(), accessToken.getSubject(), e);
            throw new RuntimeException("Failed to refresh token for subject '" + accessToken.getSubject() + "'.", e);
        }
    }

    /**
     * Asserts that the given token was created by this service (artifactory instance/cluster). This method accepts both
     * access tokens and refresh tokens, to support revoke by both. In case the token is not a valid access token (e.g.
     * a refresh token) the token is not checked.
     *
     * @param tokenValue the token to check, can be either an access token or a refresh token
     */
    private void assertTokenCreatedByThisService(@Nonnull String tokenValue) {
        try {
            JwtAccessToken accessToken = parseToken(tokenValue);
            assertTokenCreatedByThisService(accessToken);
        } catch (IllegalArgumentException e) {
            log.debug("Could not parse token value, it might be a refresh token, ignoring.", e);
        }
    }

    /**
     * Asserts that the given token was created by this service (artifactory instance/cluster).
     * This is currently important for refreshing and revoking tokens because it can only be done by the same service.
     *
     * @param accessToken the access token to check
     */
    private void assertTokenCreatedByThisService(@Nonnull JwtAccessToken accessToken) {
        String issuer = accessToken.getIssuer();
        ServiceId issuerServiceId = ServiceId.fromFormattedName(issuer);
        if (!issuerServiceId.equals(serviceId)) {
            throw new TokenIssuedByOtherServiceException("Provided access token with ID '" + accessToken.getTokenId() +
                    "' was issued by a different service with ID '" + issuerServiceId + "' (current service ID: '" +
                    serviceId + "')", serviceId, issuerServiceId);
        }
    }

    private CreatedTokenInfo toCreatedTokenInfo(TokenResponse tokenResponse, List<String> scopeTokens) {
        String scope = String.join(SCOPE_DELIMITER, scopeTokens);
        OptionalLong expiresInOptional = tokenResponse.getExpiresIn();
        Long expiresIn = expiresInOptional.isPresent() ? expiresInOptional.getAsLong() : null;
        return new CreatedTokenInfoImpl(tokenResponse.getTokenValue(), tokenResponse.getTokenType(),
                tokenResponse.getRefreshToken().orElse(null), scope, expiresIn);
    }

    private List<String> getEffectiveScope(TokenSpec tokenSpec) {
        List<String> effectiveScope = Lists.newArrayList(tokenSpec.getScope());
        addUserGroupsToScopeIfNeeded(effectiveScope, tokenSpec);
        addApiToScopeIfNeeded(effectiveScope);
        assertAcceptedScope(effectiveScope);
        return effectiveScope;
    }

    private void assertAcceptedScope(List<String> scope) {
        scope.stream()
                .filter(scopeToken ->
                        !acceptedScopePatterns.stream().anyMatch(pattern -> pattern.matcher(scopeToken).matches()))
                .findFirst()
                .ifPresent(scopeToken -> {
                    throw new IllegalArgumentException("Unaccepted scope: '" + scopeToken + "'");
                });
        if (scope.isEmpty() || scope.equals(singletonList(SCOPE_API))) {
            throw new IllegalArgumentException("Insufficient scope: '" + String.join(SCOPE_DELIMITER, scope) + "'");
        }
    }

    private void addApiToScopeIfNeeded(List<String> scope) {
        if (!scope.contains(SCOPE_API)) {
            scope.add(SCOPE_API);
        }
    }

    private void addUserGroupsToScopeIfNeeded(List<String> scope, TokenSpec tokenSpec) {
        if (tokenSpec instanceof UserTokenSpec) {
            UserInfo user = userGroupStore.findUser(((UserTokenSpec) tokenSpec).getUsername());
            if (user != null) {
                //Add user's assigned groups by default
                if (scope.isEmpty() || scope.equals(singletonList(SCOPE_API))) {
                    Set<UserGroupInfo> groups = user.getGroups();
                    if (groups != null && !groups.isEmpty()) {
                        List<String> groupNames = groups.stream()
                                .map(UserGroupInfo::getGroupName)
                                .collect(Collectors.toList());
                        String groupsConcat = String.join(",", groupNames);
                        scope.add("member-of-groups:" + groupsConcat);
                    }
                }
            }
        }
    }

    private Set<String> collectGroupNamesFromScope(List<String> scope) {
        return scope.stream()
                .filter(MemberOfGroupsScopeToken::accepts)
                .flatMap(scopeToken -> MemberOfGroupsScopeToken.parse(scopeToken).getGroupNames().stream())
                .collect(Collectors.toSet());
    }

    private List<String> getEffectiveAudience(TokenSpec tokenSpec) {
        List<String> effectiveAudience = Lists.newArrayList(tokenSpec.getAudience());
        // If audience was not specified - use this service by default, otherwise use the specified audience,
        // even if it does not contain this service.
        if (effectiveAudience.isEmpty()) {
            String thisServiceIdName = serviceId.getFormattedName();
            effectiveAudience.add(thisServiceIdName);
        } else {
            // Replace "any-type" with the artifactory type (creating tokens through artifactory allows targeting only artifactory.)
            effectiveAudience = effectiveAudience.stream()
                    .map(aud -> {
                        if ("*".equals(aud) || "*@*".equals(aud)) {
                            return ARTIFACTORY_SERVICE_TYPE + "@*";
                        }
                        return aud;
                    }).collect(Collectors.toList());
        }
        return effectiveAudience;
    }

    @Nullable
    @Override
    public String extractSubjectUsername(@Nonnull JwtAccessToken accessToken) {
        try {
            return UserTokenSpec.extractUsername(accessToken.getSubject());
        } catch (Exception e) {
            log.debug("Failed to extract subject username from access token: {}", accessToken, e);
            return null;
        }
    }

    @Nonnull
    public Collection<String> extractAppliedGroupNames(@Nonnull JwtAccessToken accessToken) {
        return collectGroupNamesFromScope(accessToken.getScope());
    }

    @Override
    public void revokeToken(@Nonnull String tokenValue) {
        assertTokenCreatedByThisService(tokenValue);
        try {
            accessClient.token().revoke(tokenValue);
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == 404) {
                throw new TokenNotFoundException("Invalid access token or refresh token", e);
            } else if (e.getStatusCode() == 403) {
                throw new AuthorizationException("Revoke token operation rejected", e);
            } else {
                throw e;
            }
        } catch (AccessClientException e) {
            String tokenId = getTokenIdFromTokenValueSafely(tokenValue, "UNKNOWN");
            log.error("Failed to revoke token with id '{}': {}", tokenId, e.getMessage());
            log.debug("Failed to revoke token with id '{}'", tokenId, e);
            throw new RuntimeException("Failed to revoke token.", e);
        }
    }

    @Override
    public void revokeTokenById(@Nonnull String tokenId) {
        try {
            boolean found = accessClient.token().revokeById(tokenId);
            if (!found) {
                throw new TokenNotFoundException("Token not found with id: " + tokenId);
            }
        } catch (AccessClientHttpException e) {
            if (e.getStatusCode() == 404) {
                throw new TokenNotFoundException("Token not found with id: " + tokenId, e);
            } else if (e.getStatusCode() == 403) {
                throw new AuthorizationException("Revoke token operation rejected", e);
            } else {
                throw e;
            }
        } catch (AccessClientException e) {
            log.error("Failed to revoke token by id '{}': {}", tokenId, e.getMessage());
            log.debug("Failed to revoke token by id '{}'", tokenId, e);
            throw new RuntimeException("Failed to revoke token by id '" + tokenId + "'", e);
        }
    }

    @Nullable
    private String getTokenIdFromTokenValueSafely(@Nonnull String tokenValue, @Nullable String defaultValue) {
        try {
            return parseToken(tokenValue).getTokenId();
        } catch (IllegalArgumentException e) {
            log.debug("Failed to parse token value, returning default value '{}' instead of the token ID.",
                    defaultValue);
            return defaultValue;
        }
    }

    @Override
    @Nonnull
    public JwtAccessToken parseToken(@Nonnull String tokenValue) throws IllegalArgumentException {
        requireNonNull(tokenValue, "Token value is required");
        return accessClient.token().parse(tokenValue);
    }

    @Override
    public boolean verifyToken(@Nonnull JwtAccessToken accessToken) {
        TokenVerifyResult result = verifyAndGetResult(accessToken);
        if (result.isSuccessful()) {
            return true;
        } else {
            log.debug("Token with id '{}' failed verification, reason: {}", accessToken.getTokenId(),
                    result.getReason());
        }
        return false;
    }

    @Override
    public TokenVerifyResult verifyAndGetResult(@Nonnull JwtAccessToken accessToken) {
        requireNonNull(accessToken, "Access token is required");
        try {
            return accessClient.token().verify(accessToken.getTokenValue());
        } catch (AccessClientException e) {
            String tokenId = accessToken.getTokenId();
            log.error("Failed to verify access token with id '{}': {}", tokenId, e.getMessage());
            log.debug("Failed to verify access token with id '{}'", tokenId, e);
            throw new RuntimeException("Failed to verify access token with id '" + tokenId + "'", e);
        }
    }

    @Nonnull
    @Override
    public ServiceId getArtifactoryServiceId() {
        return serviceId;
    }

    @Override
    public boolean isTokenAppliesScope(@Nonnull JwtAccessToken accessToken, @Nonnull String requiredScope) {
        //TODO [YA] this is currently enough, but will probably need to be more sophisticated in the near future...
        return accessToken.getScope().stream().anyMatch(scope -> scope.equals(requiredScope));
    }

    @Override
    public void reload(CentralConfigDescriptor oldDescriptor) {
        //do nothing
    }

    @Override
    public void destroy() {
        IOUtils.closeQuietly(accessClient);
    }

    @Override
    public void convert(CompoundVersionDetails source, CompoundVersionDetails target) {

    }

    @Override
    public void exportTo(ExportSettings settings) {
        if (!isUsingBundledAccessServer()) {
            log.debug("Artifactory is not using its bundled access server - skipping triggering access server export");
            return;
        }
        try {
            log.info("Triggering export in access server...");
            accessClient.useAuth(accessAdminCredentials()).system().exportAccessServer();
            Path exportEtcFolder = settings.getBaseDir().toPath().resolve("etc");
            File accessServerBackupDir = new File(artifactoryHome.getBundledAccessHomeDir(), "backup");
            Files.list(accessServerBackupDir.toPath())
                    .filter(f -> !Files.isDirectory(f))
                    .filter(f -> f.toFile().getName().matches("access\\.backup\\..+\\.json"))
                    .max((f1, f2) -> (int) (f1.toFile().lastModified() - f2.toFile().lastModified()))
                    .ifPresent(path -> copyFile(path, exportEtcFolder));
        } catch (Exception e) {
            log.debug("Error during access server backup", e);
            settings.getStatusHolder().error("Error during access server backup", e, log);
        }
    }

    private Path copyFile(Path srcFile, Path targetFolder) {
        Path trgFile = null;
        try {
            String filename = "access.bootstrap.json";
            Files.createDirectories(targetFolder);
            trgFile = targetFolder.resolve(filename);
            return Files.copy(srcFile, trgFile, COPY_ATTRIBUTES, REPLACE_EXISTING);
        } catch (IOException e) {
            String error = "Unable to copy the file from '" + srcFile + "' to '" + trgFile + "'";
            log.debug(error, e);
            throw new RuntimeException(error, e);
        }
    }

    @Override
    public void importFrom(ImportSettings settings) {
        importAccessServer(settings);
        initServiceId();
        initAccessService(true);
    }

    private void importAccessServer(ImportSettings settings) {
        if (!isUsingBundledAccessServer()) {
            log.debug("Artifactory is not using its bundled access server - skipping triggering access server import");
            return;
        }
        log.info("Triggering import in access server...");
        File bootstrapFile = new File(settings.getBaseDir(), "etc/access.bootstrap.json");
        if (bootstrapFile.exists()) {
            Path targetPath = new File(artifactoryHome.getBundledAccessHomeDir(), "etc/access.bootstrap.json").toPath();
            boolean failed = false;
            try {
                FileUtils.forceMkdir(targetPath.toFile().getParentFile());
                Files.copy(bootstrapFile.toPath(), targetPath, COPY_ATTRIBUTES, REPLACE_EXISTING);
            } catch (IOException e) {
                failed = true;
                log.debug("Failed to import access bootstrap file: {}", bootstrapFile.getAbsolutePath(), e);
                settings.getStatusHolder()
                        .error("Failed to import access bootstrap file: " + bootstrapFile.getAbsolutePath(), e, log);
            }
            if (!failed) {
                try {
                    accessClient.useAuth(accessAdminCredentials()).system().importAccessServer();
                } catch (Exception e) {
                    log.debug("Error during access server restore", e);
                    settings.getStatusHolder().error("Error during access server restore", e, log);
                }
            }
        }
    }

    private AccessAuth accessAdminCredentials() {
        String[] adminCredentials = configStore.getBootstrapAdminCredentials();
        return new AccessAuthCredentials(adminCredentials[0], adminCredentials[1]);
    }

    private boolean isUsingBundledAccessServer() {
        return configStore.isUsingBundledAccessServer();
    }

    private static class ContextStateDependantActionRunner implements ContextReadinessListener {

        private final List<Runnable> onContextCreatedActions = Lists.newArrayList();
        private boolean contextCreated = false;

        @Override
        public void onContextCreated() {
            contextCreated = true;
            onContextCreatedActions.forEach(Runnable::run);
            onContextCreatedActions.clear();
        }

        @Override
        public void onContextReady() {
            //nothing to do
        }

        public void runAfterContextCreated(Runnable action) {
            if (contextCreated) {
                action.run();
            } else {
                onContextCreatedActions.add(action);
            }
        }
    }

    void runAfterContextCreated(Runnable action) {
        contextStateDependantActionRunner.runAfterContextCreated(action);
    }

    @Override
    public void onContextCreated() {
        contextStateDependantActionRunner.onContextCreated();
    }

    @Override
    public void onContextReady() {
        contextStateDependantActionRunner.onContextReady();
    }
}

package org.artifactory.rest.common.model.xray;

import org.artifactory.descriptor.repo.XrayDescriptor;
import org.artifactory.rest.common.model.BaseModel;
import org.jfrog.client.util.PathUtils;

import static org.springframework.util.StringUtils.isEmpty;

/**
 * @author Chen Keinan
 */
public class XrayConfigModel extends BaseModel{

    private String xrayBaseUrl;
    private String xrayUser;
    private String xrayPass;
    private String artUser;
    private String artPass;
    private String artifactoryId;
    private String xrayId;

    public XrayConfigModel(){}

    public XrayConfigModel(XrayDescriptor xrayDescriptor) {
        if (xrayDescriptor != null) {
            this.setXrayUser(xrayDescriptor.getUser());
            this.setXrayPass(xrayDescriptor.getPassword());
            this.setXrayBaseUrl(xrayDescriptor.getBaseUrl());
            this.setArtifactoryId(xrayDescriptor.getArtifactoryId());
            this.setXrayId(xrayDescriptor.getXrayId());
        }
    }

    public String getXrayBaseUrl() {
        return xrayBaseUrl;
    }

    public void setXrayBaseUrl(String xrayBaseUrl) {
        this.xrayBaseUrl = xrayBaseUrl;
    }

    public String getArtUser() {
        return artUser;
    }

    public void setArtUser(String artUser) {
        this.artUser = artUser;
    }

    public String getXrayUser() {
        return xrayUser;
    }

    public void setXrayUser(String xrayUser) {
        this.xrayUser = xrayUser;
    }

    public String getXrayPass() {
        return xrayPass;
    }

    public void setXrayPass(String xrayPass) {
        this.xrayPass = xrayPass;
    }

    public String getArtifactoryId() {
        return artifactoryId;
    }

    public void setArtifactoryId(String artifactoryId) {
        this.artifactoryId = artifactoryId;
    }

    public String getArtPass() {
        return artPass;
    }

    public void setArtPass(String artPass) {
        this.artPass = artPass;
    }

    public String getXrayId() {
        return xrayId;
    }

    public void setXrayId(String xrayId) {
        this.xrayId = xrayId;
    }

    public XrayDescriptor toDescriptor(){
        XrayDescriptor xrayDescriptor = new XrayDescriptor();
        xrayDescriptor.setEnabled(true);
        xrayDescriptor.setUser(this.xrayUser);
        xrayDescriptor.setPassword(this.xrayPass);

        String xrayBaseUrl = PathUtils.addTrailingSlash(this.xrayBaseUrl);
        xrayDescriptor.setBaseUrl(xrayBaseUrl);

        xrayDescriptor.setArtifactoryId(this.artifactoryId);
        xrayDescriptor.setXrayId(this.xrayId);
        return xrayDescriptor;
    }

    public boolean validate(boolean passwordRequired) {
        if (isEmpty(this.xrayUser) ||
                (isEmpty(this.xrayPass) && passwordRequired) ||
                isEmpty(this.xrayBaseUrl) ||
                isEmpty(this.artifactoryId) ||
                isEmpty(this.xrayId)) {
            return false;
        }
        return true;
    }
}

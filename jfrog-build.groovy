#!groovy

// Please do not use stage() method just echo in this file

String revisionNumber

// This one from the root of the pipeline
def init(ctx) {
    ctx.module.deployDocker = true
    ctx.module.dockerImageNames = ['jfrog/artifactory-oss']
    ctx.module.dockerRegistry = 'art.jfrog.io'
    ctx.module.dockerLocalRepo = 'art-docker-dev-local'
}

// This one under the module without env yet
def preBuild(ctx) {
    echo "${ctx.module.name} extension env setup"
    ctx.settings.mailRecipients = ctx.settings.isRelease() ? 'stork@jfrog.com' : 'elig@jfrog.com'

    // TODO: Find if theses defaults is useful?
    //settings.resolveRepo = 'artifactory-oss-dependencies'
    //settings.deployRepo = 'libs-snapshots-local'
    ctx.module.mvnProfiles = 'release,deb,rpm,docker'
    ctx.module.addMavenOption("-T 2C")
    ctx.module.addMavenOption("-DbuildNumber.prop=${ctx.module.getRevisionNumber()}")

    ctx.common.setUiBuildResource(ctx.settings)
}

// This one under the module with env after build
def postBuild(ctx) {
    echo "Post Build Testsfor ${ctx.module.name}"
    if (!ctx.settings.isRelease() && ctx.module.deployArtifacts && ctx.module.buildBranchName == 'master') {
        parallel(
                'Trigger artifactory-sanity-5.x': {
                    retry(nRetry) {
                        build job: 'Artifactory/artifactory-sanity-5.x',
                                parameters: [
                                        string(name: 'artifactory_version', value: ARTIFACTORY_VERSION),
                                        string(name: 'artifactory_type', value: 'oss')],
                                propagate: strict, wait: strict
                    }
                },
                'artifactory-oss-5.x-db-unitest': {
                    if (ctx.settings.isRelease()) {
                        retry(nRetry) {
                            build job: 'artifactory-oss-5.x-db-unitest',
                                    parameters: [
                                            booleanParam(name: 'RUN_TEST_DERBY', value: false),
                                            booleanParam(name: 'RUN_TEST_MYSQL', value: true),
                                            booleanParam(name: 'RUN_TEST_MSSQL', value: false),
                                            booleanParam(name: 'RUN_TEST_ORACLE', value: true),
                                            booleanParam(name: 'RUN_TEST_POSTGRESQL', value: true),
                                            string(name: 'MVN_OVERWRITE_CMD',
                                                    value: '-U -B -f artifactory-oss/pom.xml -Prelease clean ' +
                                                            'install -fae ' +
                                                            '-Dmaven.repo.local=/workspace/docker-builder/build/' +
                                                            '.repository ' +
                                                            '-Dmaven.test.failure.ignore=true ' +
                                                            '-s /workspace/docker-builder/build/jenkins-settings' +
                                                            '.xml')],
                                    propagate: strict, wait: strict
                        }
                    }
                },
                'Xray Validation': { echo 'Xray validation' }
        )
    }
}


return this
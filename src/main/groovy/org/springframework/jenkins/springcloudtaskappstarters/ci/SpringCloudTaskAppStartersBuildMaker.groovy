package org.springframework.jenkins.springcloudtaskappstarters.ci

import javaposse.jobdsl.dsl.DslFactory
import org.springframework.jenkins.common.job.Cron
import org.springframework.jenkins.common.job.JdkConfig
import org.springframework.jenkins.common.job.Maven
import org.springframework.jenkins.common.job.TestPublisher
import org.springframework.jenkins.springcloudtaskappstarters.common.SpringCloudTaskAppStarterJobs

import static org.springframework.jenkins.common.job.Artifactory.artifactoryMaven3Configurator
import static org.springframework.jenkins.common.job.Artifactory.artifactoryMavenBuild

/**
 * @author Marcin Grzejszczak
 */
class SpringCloudTaskAppStartersBuildMaker implements JdkConfig, TestPublisher,
        Cron, SpringCloudTaskAppStarterJobs, Maven {

    private final DslFactory dsl
    final String organization
    final String project

    final String branchToBuild = "master"

    boolean isRelease = false

    String releaseType

    SpringCloudTaskAppStartersBuildMaker(DslFactory dsl, String organization,
                                         String project) {
        this.dsl = dsl
        this.organization = organization
        this.project = project
    }

    SpringCloudTaskAppStartersBuildMaker(DslFactory dsl, String organization,
                                         String project, boolean isRelease, String releaseType) {
        this(dsl, organization, project)
        this.isRelease = isRelease
        this.releaseType = releaseType
    }

    void deploy(boolean appsBuild = true, boolean checkTests = true,
                boolean dockerHubPush = true, boolean githubPushTrigger = true,
                boolean docsBuild = false) {
        dsl.job("${prefixJob(project)}-${branchToBuild}-ci") {
            if (githubPushTrigger && !isRelease) {
                triggers {
                    githubPush()
                }
            }
            scm {
                git {
                    remote {
                        url "https://github.com/${organization}/${project}"
                        branch branchToBuild
                    }
                }
            }

            jdk jdk8()
            wrappers {
                colorizeOutput()
                maskPasswords()
                credentialsBinding {
                    usernamePassword('DOCKER_HUB_USERNAME', 'DOCKER_HUB_PASSWORD', "hub.docker.com-springbuildmaster")
                }
            }

            steps {
                if (isRelease) {
                    if (docsBuild) {
                        shell(cleanAndInstall())
                    }
                    else if (appsBuild) {
                        shell(cleanAndDeployWithGenerateApps())
                    }
                    else {
                        shell(cleanAndDeploy())
                    }
                }
                else {
                    maven {
                        mavenInstallation(maven33())
                        if (docsBuild) {
                            goals('clean install -U -Pspring')
                        }
                        else if (appsBuild) {
                            goals('clean deploy -U -Pspring -PgenerateApps')
                        }
                        else {
                            goals('clean deploy -U -Pspring')
                        }
                    }
                }

                if (appsBuild) {
                    shell("""#!/bin/bash -x
					export MAVEN_PATH=${mavenBin()}
					${setupGitCredentials()}
					echo "Building apps"
                    cd apps
                    ../mvnw clean deploy
					${cleanGitCredentials()}
					""")
                }
                if (dockerHubPush) {
                    shell("""#!/bin/bash -x
					export MAVEN_PATH=${mavenBin()}
					${setupGitCredentials()}
					echo "Pushing to Docker Hub"
                    cd apps
                    set +x
                    ../mvnw -U --batch-mode clean package docker:build docker:push -DskipTests -Ddocker.username="\$${dockerHubUserNameEnvVar()}" -Ddocker.password="\$${dockerHubPasswordEnvVar()}"
					set -x

					${cleanGitCredentials()}
					""")
                }
            }
            configure {

                if (docsBuild) {
                    artifactoryMavenBuild(it as Node) {
                        mavenVersion(maven33())
                        if (releaseType != null && releaseType.equals("milestone")) {
                            goals('clean install -U -Pfull -Pspring -Pmilestone')
                        }
                        else {
                            goals('clean install -U -Pfull -Pspring')
                        }
                    }
                    artifactoryMaven3Configurator(it as Node) {
                        if (releaseType != null && releaseType.equals("milestone")) {
                            deployReleaseRepository("libs-milestone-local")
                        }
                    }
                }

            }

            publishers {
                //mailer('scdf-ci@pivotal.io', true, true)
                if (checkTests) {
                    archiveJunit mavenJUnitResults()
                }
            }
        }
    }
}

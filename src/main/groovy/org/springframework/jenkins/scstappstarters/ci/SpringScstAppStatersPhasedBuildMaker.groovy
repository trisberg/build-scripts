package org.springframework.jenkins.scstappstarters.ci

import org.springframework.jenkins.scstappstarters.common.AllScstAppStarterJobs
import javaposse.jobdsl.dsl.DslFactory
import org.springframework.jenkins.scstappstarters.common.SpringScstAppStarterJobs

/**
 * @author Soby Chacko
 */
class SpringScstAppStatersPhasedBuildMaker implements SpringScstAppStarterJobs {

    private final DslFactory dsl

    final String branchToBuild = "master"

    SpringScstAppStatersPhasedBuildMaker(DslFactory dsl) {
        this.dsl = dsl
    }

    void build(boolean isRelease) {
        buildAllRelatedJobs(isRelease)
        dsl.multiJob("spring-scst-app-starter-builds") {
            steps {
                if (!isRelease) {
                    phase('core-phase', 'COMPLETED') {
                        triggers {
                            githubPush()
                        }
                        scm {
                            git {
                                remote {
                                    url "https://github.com/spring-cloud-stream-app-starters/core"
                                    branch branchToBuild
                                }
                            }
                        }
                        String prefixedProjectName = prefixJob("core")
                        phaseJob("${prefixedProjectName}-${branchToBuild}-ci".toString()) {
                            currentJobParameters()
                        }
                    }
                }


                int counter = 1
                (AllScstAppStarterJobs.PHASES).each { List<String> ph ->
                    phase("app-starters-ci-group-${counter}", 'COMPLETED') {
                        ph.each {
                            String projectName ->
                                String prefixedProjectName = prefixJob(projectName)
                                phaseJob("${prefixedProjectName}-${branchToBuild}-ci".toString()) {
                                    currentJobParameters()
                                }
                        }
                    }
                    counter++;
                }

                if (!isRelease) {
                    phase('app-starters-release-phase') {
                        String prefixedProjectName = prefixJob("app-starters-release")
                        phaseJob("${prefixedProjectName}-${branchToBuild}-ci".toString()) {
                            currentJobParameters()
                        }
                    }
                }
            }
        }
    }

    void buildAllRelatedJobs(boolean isRelease) {
        if (isRelease) {
            new SpringScstAppStartersBuildMaker(dsl, "spring-cloud-stream-app-starters", "core", isRelease,
                    "1.2.0.M1", null, null, "milestone")
                    .deploy(false, false, false, false)
//            AllScstAppStarterJobs.ALL_JOBS.each {
//                new SpringScstAppStartersBuildMaker(dsl, "spring-cloud-stream-app-starters", it, isRelease,
//                        "1.1.0.RC1", "1.1.0.RC1", "Avogadro.RC1", "milestone").deploy()
//            }
            AllScstAppStarterJobs.RELEASE_ALL_JOBS.each { k, v -> new SpringScstAppStartersBuildMaker(dsl, "spring-cloud-stream-app-starters", "${k}", isRelease,
                    "${v}", "1.2.0.M1", null, "milestone").deploy()}
            new SpringScstAppStartersBuildMaker(dsl, "spring-cloud-stream-app-starters", "app-starters-release", isRelease,
                    null, "1.2.0.M1", "Bacon.M1", "milestone")
                    .deploy(false, false, false, true, true)
        }
        else {
            new SpringScstAppStartersBuildMaker(dsl, "spring-cloud-stream-app-starters", "core")
                    .deploy(false, false, false, false)
            AllScstAppStarterJobs.ALL_JOBS.each {
                new SpringScstAppStartersBuildMaker(dsl, "spring-cloud-stream-app-starters", it).deploy()
            }
            new SpringScstAppStartersBuildMaker(dsl, "spring-cloud-stream-app-starters", "app-starters-release")
                    .deploy(false, false, false, true, true)
        }


    }

}

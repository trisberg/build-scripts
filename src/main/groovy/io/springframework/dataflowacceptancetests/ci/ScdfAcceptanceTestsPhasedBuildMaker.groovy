package io.springframework.dataflowacceptancetests.ci

import javaposse.jobdsl.dsl.DslFactory
/**
 * @author Soby Chacko
 */
class ScdfAcceptanceTestsPhasedBuildMaker {


    private final DslFactory dsl

    ScdfAcceptanceTestsPhasedBuildMaker(DslFactory dsl) {
        this.dsl = dsl
    }

    void build(Map<String, List<String>> phasesInfo, Map<String, String> commands) {
        buildAllRelatedJobs(commands)
        dsl.multiJob("dataflow-acceptance-tests") {
            steps {
                phasesInfo.each {
                    k, v -> phase(k) {
                        v.each {
                            String test -> phaseJob("scdf-acceptance-tests-${test}-ci".toString()) {
                                currentJobParameters()
                            }
                        }
                    }
                }
            }
        }
    }

    void buildAllRelatedJobs(Map<String, String> commands) {
        commands.each { k, v ->
            new ScdfAcceptanceTestsBuildMaker(dsl, "spring-cloud", "spring-cloud-dataflow-acceptance-tests")
                    .deploy(k, v)
        }
    }
}
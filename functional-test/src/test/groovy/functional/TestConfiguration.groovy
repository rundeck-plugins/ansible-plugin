package functional

import org.rundeck.client.api.RundeckApi
import org.rundeck.client.api.model.ExecLog
import org.rundeck.client.api.model.ExecOutput
import org.rundeck.client.api.model.ExecutionStateResponse
import org.rundeck.client.util.Client
import spock.lang.Shared
import spock.lang.Specification

class TestConfiguration extends Specification{

    @Shared
    Client<RundeckApi> client

    ExecutionStateResponse waitForJob(String executionId){
        def finalStatus = [
                'aborted',
                'failed',
                'succeeded',
                'timedout',
                'other'
        ]

        while(true) {
            ExecutionStateResponse result=client.apiCall { api-> api.getExecutionState(executionId)}
            if (finalStatus.contains(result?.getExecutionState()?.toLowerCase())) {
                return result
            } else {
                sleep (10000)
            }
        }

    }


    List<ExecLog> getLogs(String executionId){
        def offset = 0
        def maxLines = 1000
        def lastmod = 0
        boolean isCompleted = false

        List<ExecLog> logs = []

        while (!isCompleted){
            ExecOutput result = client.apiCall { api -> api.getOutput(executionId, offset,lastmod, maxLines)}
            isCompleted = result.completed
            offset = result.offset
            lastmod = result.lastModified

            logs.addAll(result.entries)

            if(result.unmodified){
                sleep(5000)
            }else{
                sleep(2000)
            }
        }

        return logs
    }
}

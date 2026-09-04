package ro.jf.ai.assistant.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import ro.jf.ai.assistant.service.TaskService
import ro.jf.ai.assistant.transfer.CreateTaskRequest
import ro.jf.ai.assistant.transfer.UpdateTaskRequest
import ro.jf.ai.assistant.transfer.toResponse

fun Route.taskRoutes(service: TaskService) {
    route("/api/v1/tasks") {
        post {
            val task = service.create(call.receive<CreateTaskRequest>())
            call.respond(HttpStatusCode.Created, task.toResponse())
        }
        get {
            val topic = call.request.queryParameters["topic"]
            call.respond(service.list(topic).map { it.toResponse() })
        }
        get("{id}") {
            call.respond(service.get(call.parameters["id"]!!).toResponse())
        }
        put("{id}") {
            val task = service.update(call.parameters["id"]!!, call.receive<UpdateTaskRequest>())
            call.respond(task.toResponse())
        }
        delete("{id}") {
            service.delete(call.parameters["id"]!!)
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get("/api/v1/topics") {
        call.respond(service.listTopics())
    }
}

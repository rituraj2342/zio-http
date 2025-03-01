package example

import zio._

import zio.http.Header.Authorization
import zio.http._
import zio.http.codec.HttpCodec
import zio.http.endpoint._

object EndpointExamples extends ZIOAppDefault {
  import HttpCodec._

  val auth = EndpointMiddleware.auth

  // MiddlewareSpec can be added at the service level as well
  val getUser =
    Endpoint.get("users" / int("userId")).out[Int] @@ auth

  val getUserRoute =
    getUser.implement { id =>
      ZIO.succeed(id)
    }

  val getUserPosts =
    Endpoint
      .get("users" / int("userId") / "posts" / int("postId"))
      .query(query("name"))
      .out[List[String]] @@ auth

  val getUserPostsRoute =
    getUserPosts.implement[Any] { case (id1: Int, id2: Int, query: String) =>
      ZIO.succeed(List(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query"))
    }

  val routes = getUserRoute ++ getUserPostsRoute

  val app = routes.toApp(auth.implement(_ => ZIO.unit)(_ => ZIO.unit))

  val request = Request.get(url = URL.decode("/users/1").toOption.get)

  val run = Server.serve(app).provide(Server.default)

  object ClientExample {
    def example(client: Client) = {
      val locator =
        EndpointLocator.fromURL(URL.decode("http://localhost:8080").toOption.get)

      val executor: EndpointExecutor[Authorization] =
        EndpointExecutor(client, locator, ZIO.succeed(Authorization.Basic("user", "pass")))

      val x1 = getUser(42)
      val x2 = getUserPosts(42, 200, "adam")

      val result1: ZIO[Scope, Nothing, Int]          = executor(x1)
      val result2: ZIO[Scope, Nothing, List[String]] = executor(x2)

      result1.zip(result2).debug
    }
  }
}

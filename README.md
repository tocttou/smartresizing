# Smart Resizing: Seam carving

Seam carving implementation in Kotlin.
To know more: https://people.csail.mit.edu/mrub/talks/SeamCarving_6.865.pdf

Supports:
1. Image size reduction
2. Image size expansion
3. Multiple task runners using rabbitmq

## Deployment

Requires Java8+

1. Start RabbitMQ on default port.
2. Make sure to change the port in `resources/application.conf` as needed.
3. Build `smartresizing-1.0.jar` (or use the latest one from [releases](https://github.com/tocttou/smartresizing/releases)) with `./gradlew fatJar`  which produces fat jar in `build/libs/`
4. `java -cp smartresizing-1.0.jar io.ktor.server.netty.DevelopmentEngine`
5. `java -cp smartresizing-1.0.jar in.ashishchaudhary.smartresizing.taskrunner.TaskRunner`
6. Visit `localhost:8000`.

Doing `5` X times launches X workers. 

## Development

1. Clone.
2. Start RabbitMQ on default port.
3. Make sure to change the port in `resources/application.conf` as needed.
4. Use main class as `io.ktor.server.netty.DevelopmentEngine` and classpath of module `smartresizing_main`.
5. Start at least one instance of `TaskRunner`
6. Start `SmartResizing`
7. Visit `localhost:8000`

## Todo
1. Add logging.
2. Add clean up task to remove old images once in a while.

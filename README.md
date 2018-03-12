## Smart Resizing: Seam carving

Seam carving implementation in Kotlin.
To know more: https://people.csail.mit.edu/mrub/talks/SeamCarving_6.865.pdf

Supports:
1. Image size reduction
2. Image size expansion
3. Multiple task runners using rabbitmq

###Deployment

Requires Java8+

1. Start RabbitMQ on default port.
2. `java -cp smartresizing.jar io.ktor.server.netty.DevelopmentEngine`
3. `java -cp smartresizing.jar in.ashishchaudhary.smartresizing.taskrunner.TaskRunner`
4. Visit `localhost:8000`.

Doing `3` X times launches X workers. 

### Development

1. Clone.
2. Start RabbitMQ on default port.
3. Use main class as `io.ktor.server.netty.DevelopmentEngine` and classpath of module `smartresizing_main`.
4. Start at least one instance of `TaskRunner`
5. Start `SmartResizing`
6. Edit `resources.conf` to change the default port.

###Todo
1. Add logging.
2. Add clean up task to remove old images once in a while.
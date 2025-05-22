 # Config Service

 This service provides centralized configuration for other microservices in the Polar Bookshop ecosystem.

 ## Configuration

 The service can be configured using the following environment variables:

 ### Server Configuration
 - `SERVER_PORT`: The port on which the service listens (default: 8888)
 - `SERVER_HOST`: The host used for internal calls to the busrefresh endpoint (default: localhost)
   - In containerized environments like ECS, this should be set to the appropriate service name or IP address
   - For example, in ECS you might set this to the service name or the container's IP address

 ### RabbitMQ Configuration
 - `SPRING_RABBITMQ_HOST`: RabbitMQ host (default: localhost)
 - `SPRING_RABBITMQ_PORT`: RabbitMQ port (default: 5672)
 - `SPRING_RABBITMQ_USERNAME`: RabbitMQ username (default: guest)
 - `SPRING_RABBITMQ_PASSWORD`: RabbitMQ password (default: guest)
 - `SPRING_RABBITMQ_SSL_ENABLED`: Enable SSL for RabbitMQ connection (default: false)

 ## Running in ECS

 When running in ECS, make sure to set the `SERVER_HOST` environment variable to the appropriate value. This is necessary because the default value of "localhost" may not work correctly in containerized environments.

 You can set this in your ECS task definition:

 ```json
 {
   "environment": [
     {
       "name": "SERVER_HOST",
       "value": "your-service-name-or-ip"
     }
   ]
 }
 ```

 Alternatively, you can use service discovery to dynamically determine the host.

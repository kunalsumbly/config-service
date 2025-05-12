# Use a lightweight JDK base image
FROM azul/zulu-openjdk:17-latest

# Create an app directory inside the container
WORKDIR /app

RUN apt-get update && apt-get install -y curl

# Copy the jar file into /app
# Replace 'your-app.jar' with your actual jar name
COPY build/libs/config-service-0.0.1-SNAPSHOT.jar /app/config-service.jar

# Expose port 8080 (for documentation only)
EXPOSE 8080

# Command to run the app
ENTRYPOINT ["java", "-jar", "/app/config-service.jar"]

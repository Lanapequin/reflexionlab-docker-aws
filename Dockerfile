FROM eclipse-temurin:17-jdk

WORKDIR /usrapp/bin

ENV PORT 35000

COPY /target/classes /usrapp/bin/classes
COPY /target/dependency /usrapp/bin/dependency

CMD ["java", "-cp", "./classes:./dependency/*", "co.edu.escuelaing.reflexionlab.MicroSpringBoot"]
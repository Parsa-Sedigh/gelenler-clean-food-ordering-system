To check kafka cluster and topics, we use **kafka cat(kcat)** and **kafka tool**. Kafka tool is a GUI app for kafka.
We can use pgadmin.

## 003 Creating Order Service modules using Clean Architecture - PART 1
In pom.xml <packaging>, we can use two types of packaging:
- jar
- pom

The default one is jar and it will create a jar file from the module. For the main project, it doesn't make sense to use a jar packaging as
there is no source code, so we set it as pom and make it the parent pom.xml file for the child services and modules.

packaging: jar vs pom

pom packaging creates a container for submodules with jar packaging.

We set relativePath as empty. relativePath is used to search local filesystem for parents maven projects. But since we have an external
parent project which is spring boot, we don't need to search local filesystem The parent pom will be loaded from the spring boot lib.
So set the relativePath to empty for external parent projects. Use only for local parent projects.

So setting relativePath to empty is best practice if your parent is an external project like spring boot.

Make sure the correct JDK version that you installed is chosen in the `project structure` sdk field and you see the same version of jdk
when running `mvn --version` in Java version.

The module for the api layer is named `order-application`.

The idea is to develop domains separately and make other modules, plugins to the domain module.

We also need a module that will create the runnable JAR file for the microservice and have a dependency to all of to other modules.
We call this module the order-container(the grey one on top) as it will contain all other modules and also apart from creating a 
runnable JAR file, we will also create a docker image from that runnable JAR and run it on a docker container. That's why we named this module
as order-container.

Remove these from all the pom files:
```xml
<properties>
    <maven.compiler.source></maven.compiler.source>
    <maven.compiler.target></maven.compiler.target>
</properties>
```
Because we already set the maven-compiler-plugin with the right jdk version in the base pom.xml at the root. So we don't need to repeat that property.

Under order-domain, we wanna create two more submodules to separate the core domain from the application services which are the services
that expose domain methods to the outside.

We put the core logic in the `order-domain-core` module. So it will include entities, value objects and domain services.

Remove the src folder from order-domain because it's a parent module and will not include src files directly.
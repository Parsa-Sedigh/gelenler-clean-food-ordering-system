## 105-001 Introduction to Kubernetes and running a local Kubernetes using Docker Desktop
Docker desktop internally uses `kind` to enable k8s(if enabled).

## 106-002 Deploying Confluent Kafka to local Kubernetes using cp-helm-charts
Let's run kafka cluster in k8s.

The easiest way to use schema registry with a kafka cluster, is using confluent kafka operators for k8s. For production, 
it's a paid service by confluent. However, to use in test and dev env, we can still use cp-helm-chart project.

Go to infra repo.

**Clone cp-helm-chart project into the helm folder of infra project.**

Then run in the root of infra project:
```shell
helm install <a name for kafka helm cluster like local-confluent-kafka> helm/cp-helm-charts --version 0.6.0
kubectl get pods
```

Then we need to create a kafka client deployment and create the topics. For this, create kafka-client.yml and put content of
step 1 in the result of running previous helm install command there.

With k8s version 1.25, PodDistributionBudget is not in policy/v1beta. Instead it's in policy/v1. So change it in 
cp-zookeeper/poddisrubtionbudget.yml .

Then run this cmd to create a new pod using the created yml:
```shell
kubectl apply -f kafka-client.yml
```

Then go into the newly created pod:
```shell
kubectl exec -it kafka-client -- /bin/bash

# create the kafka topics
kafka-topics --zookeeper local-confluent-kafka-cp-zookeeper-headless:2181 --topic customer --create --partitions 3 --replication-factor 3 \
--if-not-exists

kafka-topics --zookeeper local-confluent-kafka-cp-zookeeper-headless:2181 --topic payment-requet --create --partitions 3 --replication-factor 3 \
--if-not-exists

kafka-topics --zookeeper local-confluent-kafka-cp-zookeeper-headless:2181 --topic payment-response --create --partitions 3 --replication-factor 3 \
--if-not-exists

kafka-topics --zookeeper local-confluent-kafka-cp-zookeeper-headless:2181 --topic restaurant-approval-request --create --partitions 3 --replication-factor 3 \
--if-not-exists

kafka-topics --zookeeper local-confluent-kafka-cp-zookeeper-headless:2181 --topic restaurant-approval-response --create --partitions 3 --replication-factor 3 \
--if-not-exists

kafka-topics --zookeeper local-confluent-kafka-cp-zookeeper-headless:2181 --list
```

To stop and remove the kafka cluster:
```shell
helm uninstall local-confluent-kafka

kubectl delete -f kafka-client.yml
```

## 107-003 Creating Kubernetes deployment files for Microservices
In each microservice, we have spring-boot-maven-plugin with `<goal>build-image</goal>`. Now when we run:
```shell
maven clean install
```
it will create the docker images with the specified names in the pom.xml files in the local docker registry.

Then run:
```shell
docker images | grep food.ordering.system
```

Now create k8s deployment files.

## 108-004 Deploying Microservices into local Kubernetes
In infra project > helm folder, deploy the kafka cluster:
```shell
helm install local-confluent-kafka cp-helm-charts --version 0.6.0
kubectl get pods
kubectl apply -f application-deployment-local.yml
```

Note: You can't run a java app with a version that is older than the compiled version. 

If you get a incompatibility version error: Remember we have `maven-compiler-plugin` in base pom.xml with the specified java version
and this is used for `compiling` our java code. Also for creating the docker image, we use `spring-boot-maven-plugin` with build-image
goal and this docker image sets our runtime version for java and uses maven.compiler.target property. So we have to add 
this property and the maven.compiler.source property to the <propeties> of base pom.xml . So the java versions of compile time and runtime
are the same.

Then install `mvn clean install` and delete application deployment in infra project by:
```shell
kubectl delete -f application-deployment-local.yml
kubectl apply -f application-deployment-local.yml
kubectl get pods
```

Now all the services should be in Running state.

## 109-005 Deploying Postgres to local Kubernetes using Postgres docker image
After creating the deployment and service for postgres, run:
```shell
kubectl apply -f postgres-deployment.yml

# in helm folder
helm install local-confluent-kafka cp-helm-charts -- version 0.6.0
```
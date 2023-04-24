# Read Me First

Sample project to demonstrate spring-boot-amqp rebinding issue for manually declared anonymous queues.

Instructions:
1. Make sure your kubernetes context is set to the right cluster (otherwise you may restart/deploy stuff you do not intend to)
2. Deploy a three member rabbit mq cluster on a kube cluster in the "test-rabbit" namespace
   2. From https://www.rabbitmq.com/kubernetes/operator/install-operator.html
   3. `kubectl apply -f "https://github.com/rabbitmq/cluster-operator/releases/latest/download/cluster-operator.yml"`
   3. Then rabbit service `kubectl apply -f src/kubernetes/rabbitmq-test.yaml`
2. Expose each member via NodePort Services and update the test.properties to match the ports (or use the generic service nodeport exposed)
   3. Defaults `messaging.addresses=localhost:31865,localhost:31866,localhost:31867
      messaging.username=test
      messaging.password=youwontguessme`
3.  Run the testRebindFail and testRebindPass to see the differences.
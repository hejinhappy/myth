package org.dromara.myth.demo.motan.account.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.dromara.myth.core.service.MythMqReceiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.List;


/**
 * The type Rocketmq consumer.
 *
 * @author xiaoyu
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.rocketmq", name = "namesrvAddr")
public class RocketmqConsumer {

    private static final String TOPIC = "account";

    @Autowired
    private Environment env;

    @Autowired(required = false)
    private MythMqReceiveService mythMqReceiveService;

    /**
     * Push consumer default mq push consumer.
     *
     * @return the default mq push consumer
     * @throws MQClientException the mq client exception
     */
    @Bean
    public DefaultMQPushConsumer pushConsumer() throws MQClientException {
        /**
         * 一个应用创建一个Consumer，由应用来维护此对象，可以设置为全局对象或者单例<br>
         * 注意：ConsumerGroupName需要由应用来保证唯一
         */
        DefaultMQPushConsumer consumer =
                new DefaultMQPushConsumer(env.getProperty("spring.rocketmq.consumerGroupName"));
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setNamesrvAddr(env.getProperty("spring.rocketmq.namesrvAddr"));
        consumer.setInstanceName(env.getProperty("spring.rocketmq.instanceName"));
        //设置批量消费，以提升消费吞吐量，默认是1
        consumer.setConsumeMessageBatchMaxSize(1);

        /**
         * 订阅指定topic下tags
         */
        consumer.subscribe(TOPIC, TOPIC);

        consumer.registerMessageListener((List<MessageExt> msgList, ConsumeConcurrentlyContext context) -> {

            MessageExt msg = msgList.get(0);
            try {
                // 默认msgList里只有一条消息，可以通过设置consumeMessageBatchMaxSize参数来批量接收消息
                final byte[] message = msg.getBody();
                final Boolean success = mythMqReceiveService.processMessage(message);
                if (success) {
                    //如果没有return success，consumer会重复消费此信息，直到success。
                    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                }

            } catch (Exception e) {
                e.printStackTrace();
                //重复消费3次
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

        });

        consumer.start();

        return consumer;
    }
}

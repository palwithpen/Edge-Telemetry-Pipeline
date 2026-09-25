package com.palwithpen.edge_telemetry_pipeline.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;

// Just the wiring for the MQTT subscriber — the connection, the channel it publishes onto,
// and the adapter that bridges Paho to Spring Integration. What actually *happens* to a
// message once it lands here lives over in ReadingMqttListener, on purpose: this class
// only builds the pipe, it doesn't know what flows through it.
@Configuration
public class MqttConfig {

    @Value ("${mqtt.broker-url}")
    private String BROKER_URL;

    @Value ("${mqtt.client-id}")
    private String CLIENT_ID;

    @Value ("${mqtt.topic}")
    private String TOPIC;

    @Bean
    public MqttPahoClientFactory mqttClientFactory(){
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();

        options.setAutomaticReconnect(true);
        options.setServerURIs(new String[]{BROKER_URL});
        // cleanSession=false: keep the broker-side session across reconnects, so anything
        // that was still unacked when we dropped off gets redelivered instead of forgotten.
        // That's what actually makes at-least-once delivery possible end to end — see the
        // manual ack in ReadingMqttListener, which is the other half of this decision.
        options.setCleanSession(false);
        options.setKeepAliveInterval(60);
        options.setConnectionTimeout(30);
        // Paho's reconnect-delay setter takes milliseconds, unlike keepAlive/connectionTimeout
        // above which are seconds — easy to get bitten by that inconsistency, so spelling out
        // the multiplication here instead of a bare "30000".
        options.setMaxReconnectDelay(30*1000);

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel(){
        return new DirectChannel();

    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(CLIENT_ID, mqttClientFactory(), TOPIC);
        // QoS 1: at-least-once. We've decided duplicates are OK (dedup handles them
        // downstream) but silently dropped readings are not.
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        // Manual acks: don't tell the broker "delivered" the instant this adapter hands the
        // message off. We only ack once ReadingWorkerPool has actually persisted it — see
        // ReadingMqttListener and ReadingWorkerPool for where the ack callback actually gets
        // carried through and fired. Without this, a crash between "received" and "saved"
        // would lose the reading while the broker thinks it succeeded.
        adapter.setManualAcks(true);
        return adapter;
    }
}

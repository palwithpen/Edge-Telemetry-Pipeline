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
        options.setCleanSession(false);
        options.setKeepAliveInterval(60);
        options.setConnectionTimeout(30);
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
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }
}

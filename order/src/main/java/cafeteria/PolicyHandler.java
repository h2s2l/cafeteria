package cafeteria;

import cafeteria.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
	
	@Autowired
	private OrderRepository orderRepository;
	
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }
    
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverCancelFailed_(@Payload CancelFailed cancelFailed){

        if(cancelFailed.isMe()){
            System.out.println("##### listener  : " + cancelFailed.toJson());
            
            Optional<Order> order = orderRepository.findById(cancelFailed.getOrderId());
            order.ifPresent(o -> {
            	o.setStatus("Ordered");
            	orderRepository.save(o);
            });
            
        }
    }

}

package cafeteria;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import cafeteria.config.kafka.KafkaProcessor;

@Service
public class PolicyHandler{
	
	@Autowired
    private PaymentRepository pyamentRepository;
	
    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverDrinkCanceled_(@Payload DrinkCanceled drinkCanceled){

        if(drinkCanceled.isMe()){
            System.out.println("##### listener  : " + drinkCanceled.toJson());
            
            List<Payment> payments = pyamentRepository.findByOrderId(drinkCanceled.getOrderId());
            for(Payment payment : payments) {
            	payment.setStatus("PaymentCanceled");
            	pyamentRepository.save(payment);
            }
        }

    }

}

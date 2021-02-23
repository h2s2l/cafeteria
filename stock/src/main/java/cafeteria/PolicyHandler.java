package cafeteria;

import cafeteria.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;


@Service
public class PolicyHandler{
	
	@Autowired
	private StockRepository stockRepository;
	
	@Autowired
	private OwnerPageRepository ownerPageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void onStringEventListener(@Payload String eventString){

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentCanceled_(@Payload PaymentCanceled paymentCanceled){

        if(paymentCanceled.isMe()){
            System.out.println("##### listener  : " + paymentCanceled.toJson());
            
            List<Stock> stocks = stockRepository.findByProductName(paymentCanceled.getProductName());
            for(Stock stock : stocks) {
            	stock.setQty(stock.getQty() + paymentCanceled.getQty());
            	stock.setStatus("UseCancled");
            	stockRepository.save(stock);
            }
        }
    }

    
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverStockCreated_(@Payload StockCreated stockCreated){

        if(stockCreated.isMe()){
            System.out.println("##### listener  : " + stockCreated.toJson());
            
			OwnerPage ownerPage = new OwnerPage();
			ownerPage.setId(stockCreated.getId());
			ownerPage.setProductName(stockCreated.getProductName());
			ownerPage.setRemainingQty(stockCreated.getQty());
			ownerPage.setUsedQty(0);
			ownerPageRepository.save(ownerPage);            
 
        }
    }
    
    
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverStockDeducted_(@Payload StockDeducted stockDeducted){

        if(stockDeducted.isMe()){
            System.out.println("##### listener  : " + stockDeducted.toJson());
            
            List<OwnerPage> ownerPages = ownerPageRepository.findByProductName(stockDeducted.getProductName());
            for(OwnerPage ownerPage : ownerPages) {
            	ownerPage.setUsedQty(ownerPage.getUsedQty() + (ownerPage.getRemainingQty() - stockDeducted.getQty()));
            	ownerPage.setRemainingQty(stockDeducted.getQty());

            	ownerPageRepository.save(ownerPage);    
            }
        }
    }
    
    
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverUseCanceled_(@Payload UseCanceled useCanceled){

        if(useCanceled.isMe()){
            System.out.println("##### listener  : " + useCanceled.toJson());
            
            List<OwnerPage> ownerPages = ownerPageRepository.findByProductName(useCanceled.getProductName());
            for(OwnerPage ownerPage : ownerPages) {
            	ownerPage.setUsedQty(ownerPage.getUsedQty() - (useCanceled.getQty() - ownerPage.getRemainingQty()));
            	ownerPage.setRemainingQty(useCanceled.getQty());

            	ownerPageRepository.save(ownerPage);    
            }
        }
    }
    
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverStockAdded_(@Payload StockAdded stockAdded){

        if(stockAdded.isMe()){
            System.out.println("##### listener  : " + stockAdded.toJson());
            
            List<OwnerPage> ownerPages = ownerPageRepository.findByProductName(stockAdded.getProductName());
            for(OwnerPage ownerPage : ownerPages) {
            	ownerPage.setRemainingQty(stockAdded.getQty());

            	ownerPageRepository.save(ownerPage);    
            }
        }
    }
    
}

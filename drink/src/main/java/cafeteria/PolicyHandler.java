package cafeteria;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import cafeteria.config.kafka.KafkaProcessor;

@Service
public class PolicyHandler {

	@Autowired
	private DrinkRepository drinkRepository;

	@StreamListener(KafkaProcessor.INPUT)
	public void onStringEventListener(@Payload String eventString) {

	}

	@StreamListener(KafkaProcessor.INPUT)
	public void wheneverOrdered_(@Payload Ordered ordered) {

		if (ordered.isMe()) {
			System.out.println("##### listener  : " + ordered.toJson());

			List<Drink> drinks = drinkRepository.findByOrderId(ordered.getId());
			for (Drink drink : drinks) {
				drink.setPhoneNumber(ordered.getPhoneNumber());
				drink.setProductName(ordered.getProductName());
				drink.setQty(ordered.getQty());
				drinkRepository.save(drink);
			}

		}
	}

	@StreamListener(KafkaProcessor.INPUT)
	public void wheneverPaymentApproved_(@Payload PaymentApproved paymentApproved) {

		if (paymentApproved.isMe()) {
			System.out.println("##### listener  : " + paymentApproved.toJson());

			Drink drink = new Drink();
			drink.setOrderId(paymentApproved.getOrderId());
			drink.setStatus(paymentApproved.getStatus());
			drinkRepository.save(drink);
		}
	}

	@StreamListener(KafkaProcessor.INPUT)
	public void wheneverOrderCanceled_(@Payload OrderCanceled orderCanceled) {

		if (orderCanceled.isMe()) {
			System.out.println("##### listener  : " + orderCanceled.toJson());

			List<Drink> drinks = drinkRepository.findByOrderId(orderCanceled.getId());
			if (drinks.stream().filter(drink -> !"PaymentApproved".equals(drink.getStatus())).count() > 0) {
				CancelFailed fail = new CancelFailed();
				fail.setOrderId(orderCanceled.getId());
				fail.setPhoneNumber(orderCanceled.getPhoneNumber());
				fail.publish();
			} else {

				for (Drink drink : drinks) {
					drink.setStatus("DrinkCancled");
					drinkRepository.save(drink);
				}
			}
		}
	}

}

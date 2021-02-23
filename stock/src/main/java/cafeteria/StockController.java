package cafeteria;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;


@RequestMapping("/stocks")
 @RestController
 public class StockController {

	@Autowired
	private StockRepository stockRepository;
	
	@PatchMapping("/useStock")
	public Stock usedStock(@RequestBody Ordered ordered) {
		List<Stock> stocks = stockRepository.findByProductName(ordered.getProductName());
		int s = ordered.getQty();
		Stock stock = stocks.get(0);
		stock.setQty(stock.getQty() - s);
		
		if(stock.getQty() < 0) throw new RuntimeException("Stock is not enough.");
		stockRepository.save(stock);
		return stock;
    }
 }

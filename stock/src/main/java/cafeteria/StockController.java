package cafeteria;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RequestMapping("/stocks")
 @RestController
 public class StockController {

	@Autowired
	private StockRepository stockRepository;
	
	@PutMapping("/useStock")
	public Stock usedStock(@RequestBody Stock stock) {
		List<Stock> stocks = stockRepository.findByProductName(stock.getProductName());
		int s = stock.getQty();
		Stock stock_temp = stocks.get(0);
		stock_temp.setQty(stock_temp.getQty() - s);
	
		if(stock_temp.getQty() < 0) {
			System.out.println(stock_temp.getProductName()+"Stock is not enough.");
			throw new RuntimeException("Stock is not enough.");
		}
		stock_temp.setStatus(stock.getStatus());
		stockRepository.save(stock_temp);
		return stock;
    }
	
	@PatchMapping("/addStock")
	public Stock addStock(@RequestBody Stock stock) {
		List<Stock> stocks = stockRepository.findByProductName(stock.getProductName());
		int s = stock.getQty();
		Stock stock_temp = stocks.get(0);
		stock_temp.setQty(stock_temp.getQty() + s);
		stock_temp.setStatus("StockAdded");
		
		System.out.println(stock_temp.getProductName()+"Stock is added.");		
		stockRepository.save(stock_temp);
		return stock;
    }
	
 }

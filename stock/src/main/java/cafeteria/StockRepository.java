package cafeteria;

import java.util.List;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface StockRepository extends PagingAndSortingRepository<Stock, Long>{

	List<Stock> findByproductName(String productName);
}
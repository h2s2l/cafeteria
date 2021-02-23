package cafeteria;

import java.util.List;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface OwnerPageRepository extends PagingAndSortingRepository<OwnerPage, Long>{

	List<OwnerPage> findByProductName(String productName);
}
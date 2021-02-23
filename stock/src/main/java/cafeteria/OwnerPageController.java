package cafeteria;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;


@RequestMapping("/ownerpages")
 @RestController
 public class OwnerPageController {

	@Autowired
	private OwnerPageRepository ownerPageRepository;
	
	@GetMapping("/{id}")
	public Optional<OwnerPage> findOwnerPage(@PathVariable("id") Long id) {
		Optional<OwnerPage> ownerPage = ownerPageRepository.findById(id);

		return ownerPage;
    }
	
	@GetMapping("/search/findByProductName")
	public List<OwnerPage> search(@RequestParam String productName) {
		List<OwnerPage> ownerPages = ownerPageRepository.findByProductName(productName);

		return ownerPages;
    }
	
 }

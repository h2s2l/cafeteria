
package cafeteria.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;

@FeignClient(name="stock", url="${feign.client.stock.url}")
public interface StockService {
	
    @RequestMapping(method= RequestMethod.PATCH, path="/stocks/useStock")
    public void useStock(@RequestBody Stock stock);

}
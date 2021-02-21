
package cafeteria.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="stock", url="${feign.client.stock.url}")
public interface StockService {

    @RequestMapping(method= RequestMethod.POST, path="/stocks")
    public void useStock(@RequestBody Stock stock);

}
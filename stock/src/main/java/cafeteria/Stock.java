package cafeteria;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;



import java.util.List;

@Entity
@Table(name="Stock")
public class Stock {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String productName;
    private Integer qty;
    private String status="Created";
    
    

	@PostPersist
    public void onPostPersist(){
    	StockCreated stockCreated = new StockCreated();
        BeanUtils.copyProperties(this, stockCreated);
        stockCreated.publishAfterCommit();
    }

    @PostUpdate
    public void onPostUpdate(){    	
    	
   	switch(status) {
   		case "StockDeducted" : 
	        StockDeducted stockDeducted = new StockDeducted();
	        BeanUtils.copyProperties(this, stockDeducted);
	        stockDeducted.publishAfterCommit();
	        try {
	            Thread.currentThread().sleep((long) (400 + Math.random() * 220));
	        }catch (InterruptedException e) {
	        	e.printStackTrace();
	        }
	        break;
    	case "StockAdded" : 
    		StockAdded stockAdded = new StockAdded();
            BeanUtils.copyProperties(this, stockAdded);
            stockAdded.publishAfterCommit();
            break;
    	case "UseCancled" : 
            UseCanceled useCanceled = new UseCanceled();
            BeanUtils.copyProperties(this, useCanceled);
            useCanceled.publishAfterCommit();
            break;

    	}    	
    	
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}


}

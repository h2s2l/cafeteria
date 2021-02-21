package cafeteria;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;



import java.util.List;

@Entity
@Table(name="Stock_table")
public class Stock {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String productName;
    private Integer pty;
    private String status;
    
    

	@PostPersist
    public void onPostPersist(){
    	StockAdded stockAdded = new StockAdded();
        BeanUtils.copyProperties(this, stockAdded);
        stockAdded.publishAfterCommit();
    }
    

    @PostUpdate
    public void onPostUpdate(){    	
    	
   	switch(status) {
    	case "StockDeducted" : 
            StockDeducted stockDeducted = new StockDeducted();
            BeanUtils.copyProperties(this, stockDeducted);
            stockDeducted.publishAfterCommit();
            break;
    	case "PaymentCanceled" : 
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
    public Integer getPty() {
        return pty;
    }

    public void setPty(Integer pty) {
        this.pty = pty;
    }

    public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}


}

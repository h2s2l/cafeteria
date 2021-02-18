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
    
    
    @PostPersist
    public void onPostPersist(){
    	StockAdded stockAdded = new StockAdded();
        BeanUtils.copyProperties(this, stockAdded);
        stockAdded.publishAfterCommit();
    }
    

    @PostUpdate
    public void onPostUpdate(){    	
    	
 /*   	switch(status) {
    	case "Receipted" : 
    		Receipted receipted = new Receipted();
            BeanUtils.copyProperties(this, receipted);
            receipted.publishAfterCommit();
            break;
    	case "Made" : 
    		Made made = new Made();
            BeanUtils.copyProperties(this, made);
            made.publishAfterCommit();
            break;
    	case "DrinkCancled" : 
    		DrinkCanceled drinkCanceled = new DrinkCanceled();
            BeanUtils.copyProperties(this, drinkCanceled);
            drinkCanceled.publishAfterCommit();
            break;
    	}
    	
    	
  */  	
        StockDeducted stockDeducted = new StockDeducted();
        BeanUtils.copyProperties(this, stockDeducted);
        stockDeducted.publishAfterCommit();


        UseCanceled useCanceled = new UseCanceled();
        BeanUtils.copyProperties(this, useCanceled);
        useCanceled.publishAfterCommit();


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




}

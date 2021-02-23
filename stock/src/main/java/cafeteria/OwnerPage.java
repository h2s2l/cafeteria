package cafeteria;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name="OwnerPage_table")
public class OwnerPage {

        @Id
        @GeneratedValue(strategy=GenerationType.AUTO)
        private Long id;
        private String productName;
        private Integer remainingQty;
        private Integer usedQty;


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
        public Integer getRemainingQty() {
            return remainingQty;
        }

        public void setRemainingQty(Integer remainingQty) {
            this.remainingQty = remainingQty;
        }
        public Integer getUsedQty() {
            return usedQty;
        }

        public void setUsedQty(Integer usedQty) {
            this.usedQty = usedQty;
        }

}

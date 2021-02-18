package cafeteria;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name="OnwerPage_table")
public class OnwerPage {

        @Id
        @GeneratedValue(strategy=GenerationType.AUTO)
        private Long id;
        private String productName;
        private Integer qty;
        private Integer usedPty;


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
        public Integer getUsedPty() {
            return usedPty;
        }

        public void setUsedPty(Integer usedPty) {
            this.usedPty = usedPty;
        }

}

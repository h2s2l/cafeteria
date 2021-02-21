package cafeteria.external;

public class Stock {

    private Long id;
    private String productName;
    private Integer pty;
    private String status = "StockDeducted";
    

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

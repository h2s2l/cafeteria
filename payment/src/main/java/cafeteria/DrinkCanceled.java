package cafeteria;

public class DrinkCanceled extends AbstractEvent {

    private Long id;
    private Long orderId;
    private String phoneNumber;
    private String status;

    public DrinkCanceled(){
        super();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
    public String getPhoneNumber() {
    	return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
    	this.phoneNumber = phoneNumber;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
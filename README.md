# cafeteria
# Table of contents

- [음료주문](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [DDD 의 적용](#ddd-의-적용)
    - [API Gateway](#API-GATEWAY)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
    - [Saga Pattern / 보상 트랜잭션](#Saga-Pattern--보상-트랜잭션)
    - [CQRS / Meterialized View](#CQRS--Meterialized-View)
  - [운영](#운영)
    - [Liveness / Readiness 설정](#Liveness--Readiness-설정)
    - [CI/CD 설정](#cicd-설정)
    - [Self Healing](#Self-Healing)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)
    - [모니터링](#모니터링)
    - [Persistence Volum Claim](#Persistence-Volum-Claim)
    - [ConfigMap / Secret](#ConfigMap--Secret)

# 서비스 시나리오

음료주문

기능적 요구사항
1. 오너가 재고를 생성/추가한다.
1. 고객이 음료를 주문한다.
1. 음료가 주문되면 재고를 사용한다.
1. 고객이 결제한다.
1. 결제가 되면 주문 내역이 바리스타에게 전달된다.
1. 바리스타는 주문내역을 확인하여 음료를 접수하고 제조한다.
1. 고객이 주문을 취소할 수 있다.
1. 주문이 취소되면 음료를 취소한다.
1. 음료가 취소되면 결제를 취소한다.
1. 결제가 취소되면 재고를 보충한다.
1. 고객이 주문상태를 중간중간 조회한다.
1. 주문상태가 바뀔 때 마다 카톡으로 알림을 보낸다.

비기능적 요구사항
1. 트랜잭션
    1. 결제가 되지 않은 주문건은 아예 거래가 성립되지 않아야 한다.  Sync 호출
    1. 재고차감이 되지 않은 주문건은 거래가 성립되지 않아야 한다. Sync 호출 
    1. 주문이 취소되어도 바리스타가 접수하여 음료제조를 시작한 주문인 경우 주문 취소는 원복되어야 한다.  Saga(보상 트랜잭션)
    1. 주문이 취소되면 사용한 재고를 원복되어야 한다.  Saga(보상 트랜잭션)
1. 장애격리
    1. 음료제조 기능이 수행되지 않더라도 주문은 받을 수 있어야 한다.  Async (event-driven), Eventual Consistency
    1. 재고 기능이 수행되지 않더라도 주문취소가 가능해야 한다.  Async (event-driven), Eventual Consistency
    1. 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다.  Circuit breaker, fallback
    1. 재고 기능이 과중되면 사용자를 잠시동안 받지 않고 주문을 잠시후에 하도록 유도한다.  Circuit breaker, fallback
1. 성능
    1. 고객이 자주 확인할 수 있는 주문상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다.  CQRS
    1. 오너는 남은재고와 재고사용량를 오너페이지(프론트엔드)에서 확인할 수 있어야 한다.  CQRS
    1. 주문상태가 바뀔때마다 카톡 등으로 알림을 줄 수 있어야 한다.  Event driven

# 분석설계

1. Event Storming 모델
![image](https://user-images.githubusercontent.com/76020485/108925819-a15c3c00-7680-11eb-9a59-8ffb90429515.PNG)
1. 헥사고날 아키텍처 다이어그램 도출
![image](https://user-images.githubusercontent.com/76020485/108991056-f331b000-76da-11eb-866a-a69d7021c83e.PNG)

# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n
이다)

```
cd order
mvn spring-boot:run

cd payment
mvn spring-boot:run 

cd drink
mvn spring-boot:run  

cd customercneter
mvn spring-boot:run

cd stock
mvn spring-boot:run
```

## DDD 의 적용

- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다

```
package cafeteria;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.Table;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import cafeteria.external.Payment;
import cafeteria.external.PaymentService;

@Entity
@Table(name="ORDER_MANAGEMENT")
public class Order {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String phoneNumber;
    private String productName;
    private Integer qty;
    private Integer amt;
    private String status = "Ordered";

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    
    public String getPhoneNumber() {
    	return phoneNumber;
    }
    public void setPhoneNumber(String phoneNumber) {
    	this.phoneNumber = phoneNumber;
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
    public Integer getAmt() {
        return amt;
    }

    public void setAmt(Integer amt) {
        this.amt = amt;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다
```
package cafeteria;

import org.springframework.data.repository.PagingAndSortingRepository;

public interface OrderRepository extends PagingAndSortingRepository<Order, Long>{
}
```
- 적용 후 REST API 의 테스트
```
#시나리오1
재고 생성/추가 후 주문처리 및 완료

# stock - coffee1 음료 재고 생성

root@siege:/# http stock:8080/stocks productName="coffee1" qty=100
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 12:22:01 GMT
Location: http://stock:8080/stocks/1
Transfer-Encoding: chunked
{
    "_links": {
        "self": {
            "href": "http://stock:8080/stocks/1"
        },
        "stock": {
            "href": "http://stock:8080/stocks/1"
        }
    },
    "productName": "coffee1",
    "qty": 100,
    "status": "Created"
}

# stock - 음료 재고 추가

root@siege:/# http PATCH stock:8080/stocks/addStock productName="coffee1" qty=30
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 12:24:19 GMT
Transfer-Encoding: chunked

{
    "id": null,
    "productName": "coffee1",
    "qty": 30,
    "status": "Created"
}

# stock - 음료 재고 추가 확인

root@siege:/# http http://stock:8080/stocks/1
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Tue, 23 Feb 2021 12:25:22 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "self": {
            "href": "http://stock:8080/stocks/1"
        },
        "stock": {
            "href": "http://stock:8080/stocks/1"
        }
    },
    "productName": "coffee1",
    "qty": 130,
    "status": "StockAdded"
}


# order - 음료 주문

root@siege:/# http order:8080/orders phoneNumber="01012341234" productName="coffee1" qty=1 amt=2000
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 12:26:23 GMT
Location: http://order:8080/orders/2
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/2"
        },
        "self": {
            "href": "http://order:8080/orders/2"
        }
    },
    "amt": 2000,
    "createTime": "2021-02-23T12:26:23.350+0000",
    "phoneNumber": "01012341234",
    "productName": "coffee1",
    "qty": 1,
    "status": "Ordered"
}


# stock - 재고 사용 확인

root@siege:/# http http://stock:8080/stocks/1
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Tue, 23 Feb 2021 12:26:51 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "self": {
            "href": "http://stock:8080/stocks/1"
        },
        "stock": {
            "href": "http://stock:8080/stocks/1"
        }
    },
    "productName": "coffee1",
    "qty": 128,
    "status": "StockDeducted"
}


# drink - 바리스타의 음료 접수

root@siege:/# http PATCH http://drink:8080/drinks/2 status="Receipted"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 12:28:14 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/2"
        },
        "self": {
            "href": "http://drink:8080/drinks/2"
        }
    },
    "createTime": "2021-02-23T12:26:23.563+0000",
    "orderId": 2,
    "phoneNumber": "01012341234",
    "productName": "coffee1",
    "qty": 1,
    "status": "Receipted"
}



# drink - 바리스타의 음료 제조

root@siege:/# http PATCH http://drink:8080/drinks/2 status="Made"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 12:28:47 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/2"
        },
        "self": {
            "href": "http://drink:8080/drinks/2"
        }
    },
    "createTime": "2021-02-23T12:26:23.563+0000",
    "orderId": 2,
    "phoneNumber": "01012341234",
    "productName": "coffee1",
    "qty": 1,
    "status": "Made"
}




#시나리오2
음료 주문 후 주문 취소 시 재고 원복 확인


# stock - coffee2 음료 재고 생성
root@siege:/# http stock:8080/stocks productName="coffee2" qty=50
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 13:27:22 GMT
Location: http://stock:8080/stocks/2
Transfer-Encoding: chunked

{
    "_links": {
        "self": {
            "href": "http://stock:8080/stocks/2"
        },
        "stock": {
            "href": "http://stock:8080/stocks/2"
        }
    },
    "productName": "coffee2",
    "qty": 50,
    "status": "Created"
}

# order - 음료 주문
root@siege:/# http order:8080/orders phoneNumber="01011112222" productName="coffee2" qty=3 amt=9000
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 13:28:26 GMT
Location: http://order:8080/orders/3
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/3"
        },
        "self": {
            "href": "http://order:8080/orders/3"
        }
    },
    "amt": 9000,
    "createTime": "2021-02-23T13:28:26.078+0000",
    "phoneNumber": "01011112222",
    "productName": "coffee2",
    "qty": 3,
    "status": "Ordered"
}

# payment - 결제확인
root@siege:/# http http://payment:8080/payments/search/findByOrderId?orderId=3
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Tue, 23 Feb 2021 13:29:09 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "payments": [
            {
                "_links": {
                    "payment": {
                        "href": "http://payment:8080/payments/3"
                    },
                    "self": {
                        "href": "http://payment:8080/payments/3"
                    }
                },
                "amt": 9000,
                "createTime": "2021-02-23T13:28:26.153+0000",
                "orderId": 3,
                "phoneNumber": "01011112222",
                "productName": "coffee2",
                "qty": 3,
                "status": "PaymentApproved"
            }
        ]
    },
    "_links": {
        "self": {
            "href": "http://payment:8080/payments/search/findByOrderId?orderId=3"
        }
    }
}



# stock - 음료 재고 소진 확인
root@siege:/# http http://stock:8080/stocks/2
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Tue, 23 Feb 2021 13:29:32 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "self": {
            "href": "http://stock:8080/stocks/2"
        },
        "stock": {
            "href": "http://stock:8080/stocks/2"
        }
    },
    "productName": "coffee2",
    "qty": 47,
    "status": "StockDeducted"
}



# order - 음료 주문취소
root@siege:/# http patch http://order:8080/orders/3 status="OrderCanceled"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 13:29:59 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/3"
        },
        "self": {
            "href": "http://order:8080/orders/3"
        }
    },
    "amt": 9000,
    "createTime": "2021-02-23T13:28:26.078+0000",
    "phoneNumber": "01011112222",
    "productName": "coffee2",
    "qty": 3,
    "status": "OrderCanceled"
}


# stock - 재고 복구
root@siege:/# http http://stock:8080/stocks/2
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Tue, 23 Feb 2021 13:30:21 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "self": {
            "href": "http://stock:8080/stocks/2"
        },
        "stock": {
            "href": "http://stock:8080/stocks/2"
        }
    },
    "productName": "coffee2",
    "qty": 50,
    "status": "UsedCancled"
}


# OwnerPage 시나리오
OwnerPage에서 Owner는 재고상태와 소진된양을 확인 할 수 있다

# 음료 생성
root@siege:/# http stock:8080/stocks productName="coffee1" qty=100
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 16:31:51 GMT
Location: http://stock:8080/stocks/1
Transfer-Encoding: chunked

{
    "_links": {
        "self": {
            "href": "http://stock:8080/stocks/1"
        },
        "stock": {
            "href": "http://stock:8080/stocks/1"
        }
    },
    "productName": "coffee1",
    "qty": 100,
    "status": "Created"
}


# 오너페이지 재고 확인
root@siege:/# http http://stock:8080/ownerpages/2
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 16:32:04 GMT
Transfer-Encoding: chunked

{
    "id": 2,
    "productName": "coffee1",
    "remainingQty": 100,
    "usedQty": 0
}

```

## API Gateway
API Gateway를 통하여 동일 진입점으로 진입하여 각 마이크로 서비스를 접근할 수 있다.
외부에서 접근을 위하여 Gateway의 Service는 LoadBalancer Type으로 생성했다.

```
# application.yml

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: order
          uri: http://order:8080
          predicates:
            - Path=/orders/** 
        - id: payment
          uri: http://payment:8080
          predicates:
            - Path=/payments/** 
        - id: drink
          uri: http://drink:8080
          predicates:
            - Path=/drinks/**,/orderinfos/**
        - id: customercenter
          uri: http://customercenter:8080
          predicates:
            - Path= /mypages/**
        - id: stock
          uri: http://stock:8080
          predicates:
            - Path=/stocks/**



# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: gateway
  labels:
    app: gateway
spec:
  type: LoadBalancer
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: gateway
    
$ kubectl get svc
NAME             TYPE           CLUSTER-IP       EXTERNAL-IP                                                                  PORT(S)          AGE
customercenter   ClusterIP      10.100.52.95     <none>                                                                       8080/TCP         9h
drink            ClusterIP      10.100.136.6     <none>                                                                       8080/TCP         9h
gateway          LoadBalancer   10.100.164.152   a6826d83b5c8e4f5dad7129c7cdf0ded-93964597.ap-northeast-2.elb.amazonaws.com   8080:30109/TCP   9h
order            ClusterIP      10.100.197.15    <none>                                                                       8080/TCP         9h
payment          ClusterIP      10.100.242.153   <none>                                                                       8080/TCP         9h

```
 - order  
![image](https://user-images.githubusercontent.com/76020485/108672134-e53a2e80-7524-11eb-8008-ebcfbd8e9cbe.PNG)
 - payment  
![image](https://user-images.githubusercontent.com/76020485/108672136-e5d2c500-7524-11eb-824e-4066bb87376b.PNG)
 - drink  
![image](https://user-images.githubusercontent.com/76020485/108672138-e66b5b80-7524-11eb-9c27-cf2089f4ac08.PNG)
 - customercenter  
![image](https://user-images.githubusercontent.com/76020485/108672131-e4a19800-7524-11eb-894e-832ed6519b53.PNG)

## 폴리글랏 퍼시스턴스

재고(stock)관리 서비스는 Spring에서 제공하는 Embedded Datadase인 HSQL을 사용하였다.
HSQL 적용을 위하여 stock의 pom.xml에 아래의 dependency설정하였다.
```
<!-- HSQL -->
<dependency>
   <groupId>org.hsqldb</groupId>
   <artifactId>hsqldb</artifactId>
   <version>2.4.0</version>
   <scope>runtime</scope>
</dependency>
```


## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문시 재고 확인및 사용을 위하여  주문(order) -> 재고(stock)간의 호출에서 동기식 호출을 사용하였으며, FeignClient를 이용하여 호출하였다.

주문 시 (@PostPersist) 재고사용(StockService의 useStock)
```

# Order.java
    @PostPersist
    public void onPostPersist(){
	Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();
        
        
        Stock stock = new Stock();
        stock.setProductName(this.productName);
        stock.setQty(this.qty);
        
        OrderApplication.applicationContext.getBean(StockService.class).useStock(stock);        
	.....



# StockService.java

@FeignClient(name="stock", url="${feign.client.stock.url}")
public interface StockService {
	
    @RequestMapping(method= RequestMethod.PUT, path="/stocks/useStock")
    public void useStock(@RequestBody Stock stock);

}


```
재고관리 서비스의 장애시 주문접수 불가 확인

```
root@siege-5c7c46b788-zjctw:/# http order:8080/orders phoneNumber="01012341234" productName="coffee1" qty=1 amt=2000
HTTP/1.1 500 
Connection: close
Content-Type: application/json;charset=UTF-8
Date: Wed, 24 Feb 2021 11:30:56 GMT
Transfer-Encoding: chunked

{
    "error": "Internal Server Error",
    "message": "Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction",
    "path": "/orders",
    "status": 500,
    "timestamp": "2021-02-24T11:30:56.943+0000"
}
```



## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 취소시 재고 원복은 비동기식으로 처리하여 결제 취소 시 재고서비스의 상태에 따른 영향도를 제거하였다.
 
- 이를 위하여 결제이력에 기록을 남긴 후에 곧바로 결제승인이 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```
# Payment.java

    @PostUpdate
    public void onPostUpdate(){
        PaymentCanceled paymentCanceled = new PaymentCanceled();
        BeanUtils.copyProperties(this, paymentCanceled);
        paymentCanceled.publishAfterCommit();

    }

```
- 재고 서비스에서는 결제취소(PaymentCanceled) 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
# (stock) PolicyHandler.java

   @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentCanceled_(@Payload PaymentCanceled paymentCanceled){

        if(paymentCanceled.isMe()){
            System.out.println("##### listener  : " + paymentCanceled.toJson());
            
            List<Stock> stocks = stockRepository.findByProductName(paymentCanceled.getProductName());
            for(Stock stock : stocks) {
            	stock.setQty(stock.getQty() + paymentCanceled.getQty());
            	stock.setStatus("UseCancled");
            	stockRepository.save(stock);
            }
        }

```
Replica를 추가했을 때 중복없이 수신할 수 있도록 서비스별 Kafka Group을 동일하게 지정했다.
```
spring:
  cloud:
    stream:
      bindings:
        event-in:
          group: stock
          destination: cafeteria
          contentType: application/json
        :
```

재고 서비스 장애 시 주문 취소 시나리오  
```
# 음료 서비스 (drink) 를 잠시 내려놓음
$ kubectl delete deploy drink
deployment.apps "drink" deleted

#주문처리
root@siege-5b99b44c9c-8qtpd:/# http http://order:8080/orders phoneNumber="01012345679" productName="coffee" qty=3 amt=5000
HTTP/1.1 201 
Content-Type: application/json;charset=UTF-8
Date: Sat, 20 Feb 2021 14:53:25 GMT
Location: http://order:8080/orders/7
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/7"
        },
        "self": {
            "href": "http://order:8080/orders/7"
        }
    },
    "amt": 5000,
    "createTime": "2021-02-20T14:53:25.115+0000",
    "phoneNumber": "01012345679",
    "productName": "coffee",
    "qty": 3,
    "status": "Ordered"
}
#음료 서비스 기동
kubectl apply -f deployment.yml
deployment.apps/drink created

#음료등록 확인

root@siege-5b99b44c9c-8qtpd:/# http http://drink:8080/drinks/search/findByOrderId?orderId=7
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 14:54:14 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "drinks": [
            {
                "_links": {
                    "drink": {
                        "href": "http://drink:8080/drinks/4"
                    },
                    "self": {
                        "href": "http://drink:8080/drinks/4"
                    }
                },
                "createTime": "2021-02-20T14:53:25.194+0000",
                "orderId": 7,
                "phoneNumber": "01012345679",
                "productName": "coffee",
                "qty": 3,
                "status": "PaymentApproved"
            }
        ]
    },
    "_links": {
        "self": {
            "href": "http://drink:8080/drinks/search/findByOrderId?orderId=7"
        }
    }
}

```


## Saga Pattern / 보상 트랜잭션

음료 주문 취소는 바리스타가 음료 접수하기 전에만 취소가 가능하다.
음료 접수 후에 취소할 경우 보상트랜재션을 통하여 취소를 원복한다.
음료 주문 취소는 Saga Pattern으로 만들어져 있어 바리스타가 음료를 이미 접수하였을 경우 취소실패를 Event로 publish하고
Order 서비스에서 취소실패 Event를 Subscribe하여 주문취소를 원복한다.
```
# 주문
root@siege-5b99b44c9c-8qtpd:/# http http://order:8080/orders/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:58:19 GMT
Transfer-Encoding: chunked
{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/5"
        },
        "self": {
            "href": "http://order:8080/orders/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T08:51:17.441+0000",
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Ordered"
}

# 결제 상태 확인 
root@siege-5b99b44c9c-8qtpd:/# http http://payment:8080/payments/search/findByOrderId?orderId=5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:58:54 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "payments": [
            {
                "_links": {
                    "payment": {
                        "href": "http://payment:8080/payments/5"
                    },
                    "self": {
                        "href": "http://payment:8080/payments/5"
                    }
                },
                "amt": 100,
                "createTime": "2021-02-20T08:51:17.452+0000",
                "orderId": 5,
                "phoneNumber": "01033132570",
                "status": "PaymentApproved"
            }
        ]
    },
    "_links": {
        "self": {
            "href": "http://payment:8080/payments/search/findByOrderId?orderId=5"
        }
    }
}

# 음료 상태 확인
root@siege-5b99b44c9c-8qtpd:/# http http://drink:8080/drinks/search/findByOrderId?orderId=5                              
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:52:14 GMT
Transfer-Encoding: chunked

{
    "_embedded": {
        "drinks": [
            {
                "_links": {
                    "drink": {
                        "href": "http://drink:8080/drinks/5"
                    },
                    "self": {
                        "href": "http://drink:8080/drinks/5"
                    }
                },
                "createTime": "2021-02-20T08:51:17.515+0000",
                "orderId": 5,
                "phoneNumber": "01033132570",
                "productName": "coffee",
                "qty": 2,
                "status": "PaymentApproved"
            }
        ]
    },
    "_links": {
        "self": {
            "href": "http://drink:8080/drinks/search/findByOrderId?orderId=5"
        }
    }
}

# 음료 접수
root@siege-5b99b44c9c-8qtpd:/# http patch http://drink:8080/drinks/5 status="Receipted"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:53:29 GMT
Transfer-Encoding: chunked
{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/5"
        },
        "self": {
            "href": "http://drink:8080/drinks/5"
        }
    },
    "createTime": "2021-02-20T08:51:17.515+0000",
    "orderId": 5,
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Receipted"
}

# 주문 취소
root@siege-5b99b44c9c-8qtpd:/# http patch http://order:8080/orders/5 status="OrderCanceled"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Sat, 20 Feb 2021 08:54:29 GMT
Transfer-Encoding: chunked
{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/5"
        },
        "self": {
            "href": "http://order:8080/orders/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T08:51:17.441+0000",
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "OrderCanceled"
}

# 주문 조회
root@siege-5b99b44c9c-8qtpd:/# http http://order:8080/orders/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 09:07:49 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "order": {
            "href": "http://order:8080/orders/5"
        },
        "self": {
            "href": "http://order:8080/orders/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T09:07:24.114+0000",
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Ordered"
}

# 결제 상태 확인
root@siege-5b99b44c9c-8qtpd:/# http http://payment:8080/payments/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 09:21:59 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "payment": {
            "href": "http://payment:8080/payments/5"
        },
        "self": {
            "href": "http://payment:8080/payments/5"
        }
    },
    "amt": 100,
    "createTime": "2021-02-20T08:51:17.452+0000",
    "orderId": 5,
    "phoneNumber": "01033132570",
    "status": "PaymentApproved"
}

# 음료 상태 확인
root@siege-5b99b44c9c-8qtpd:/# http http://drink:8080/drinks/5
HTTP/1.1 200 
Content-Type: application/hal+json;charset=UTF-8
Date: Sat, 20 Feb 2021 09:22:47 GMT
Transfer-Encoding: chunked

{
    "_links": {
        "drink": {
            "href": "http://drink:8080/drinks/5"
        },
        "self": {
            "href": "http://drink:8080/drinks/5"
        }
    },
    "createTime": "2021-02-20T08:51:17.515+0000",
    "orderId": 5,
    "phoneNumber": "01033132570",
    "productName": "coffee",
    "qty": 2,
    "status": "Receipted"
}

```

CancelFailed Event는 Customercenter 서비스에서도 subscribe하여 카카오톡으로 취소 실패된 내용을 전달한다.
```
2021-02-20 09:08:42.668  INFO 1 --- [container-0-C-1] cafeteria.external.KakaoServiceImpl      :
To. 01033132570
Your Order is already started. You cannot cancel!!
```

## CQRS / Meterialized View

stock의 Ownerpage를 구현하여 재고의 남은상태와 사용상태를 조회할 수 있다.
```
# 오너페이지 재고 사용 확인
root@siege:/# http http://stock:8080/ownerpages/2
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 16:33:01 GMT
Transfer-Encoding: chunked

{
    "id": 2,
    "productName": "coffee1",
    "remainingQty": 80,
    "usedQty": 50
}

# 오너페이지 제품명으로 검색
root@siege:/# http http://stock:8080/ownerpages/search/findByProductName?productName="coffee1"
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Date: Tue, 23 Feb 2021 16:34:55 GMT
Transfer-Encoding: chunked

[
    {
        "id": 2,
        "productName": "coffee1",
        "remainingQty": 130,
        "usedQty": 0
    }
]

```



# 운영

## Liveness / Readiness 설정
Pod 생성 시 준비되지 않은 상태에서 요청을 받아 오류가 발생하지 않도록 Readiness Probe와 Liveness Probe를 설정했다.
```
apiVersion: apps/v1
kind: Deployment
metadata:
  name: stock
  namespace: stock
  labels:
    app: stock
spec:
  replicas: 1
  selector:
    matchLabels:
      app: stock
  template:
    metadata:
      labels:
        app: stock
    spec:
      containers:
        - name: stock
          image: h2s2l/stock:v1
          ports:
            - containerPort: 8080
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```

## Self Healing
livenessProbe를 설정하여 문제가 있을 경우 스스로 재기동 되도록 한다.
health check의 httpGet 정보를 임의로 수정하여 문제상황을 가정하였으며 describe를 이용하여 pod의 재기동 상태를 확인하였다.



```	
# stock의 deployment
 livenessProbe:
   httpGet:
     path: /actuator/health
     port: 8080
   initialDelaySeconds: 120
   timeoutSeconds: 2
   periodSeconds: 5
   failureThreshold: 5

$ kubectl describe pods stock-7f57cf5f9f-csp2b
:
Events:
  Type     Reason     Age                   From     Message
  ----     ------     ----                  ----     -------
  Normal   Killing    12m (x2 over 6h21m)   kubelet  Container customercenter failed liveness probe, will be restarted
  Normal   Pulling    12m (x3 over 20h)     kubelet  Pulling image "496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/skteam04/customercenter:v1"
  Normal   Created    12m (x3 over 20h)     kubelet  Created container customercenter
  Normal   Started    12m (x3 over 20h)     kubelet  Started container customercenter
  Normal   Pulled     12m (x3 over 20h)     kubelet  Successfully pulled image "496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/skteam04/customercenter:v1"
  Warning  Unhealthy  11m (x30 over 20h)    kubelet  Readiness probe failed: Get http://10.64.1.29:8080/actuator/health: dial tcp 10.64.1.29:8080: connect: connection refused
  Warning  Unhealthy  11m (x17 over 6h21m)  kubelet  Readiness probe failed: Get http://10.64.1.29:8080/actuator/health: net/http: request canceled (Client.Timeout exceeded while awaiting headers)
  Warning  Unhealthy  14s                   kubelet  Readiness probe failed: HTTP probe failed with statuscode: 503
  Warning  Unhealthy  11s (x13 over 6h21m)  kubelet  Liveness probe failed: Get http://10.64.1.29:8080/actuator/health: net/http: request canceled (Client.Timeout exceeded while awaiting headers)
  


```

## CI/CD 설정


각 구현체들은 하나의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS를 사용하였으며, pipeline build script는 각 프로젝트 폴더 아래에 buildspec.yml 에 포함되었다.

![image](https://user-images.githubusercontent.com/76020485/109002030-0481b900-76e9-11eb-82e4-d5709051992a.PNG)
![image](https://user-images.githubusercontent.com/76020485/109002045-08154000-76e9-11eb-8af6-9c215c0495b7.PNG)
![image](https://user-images.githubusercontent.com/76020485/109002039-064b7c80-76e9-11eb-9454-9fb3c11f631c.PNG)
![image](https://user-images.githubusercontent.com/76020485/109002016-00559b80-76e9-11eb-8bba-b3c2761aac69.PNG)
![image](https://user-images.githubusercontent.com/76020485/109002002-fcc21480-76e8-11eb-825d-ba4ece827676.PNG)
![image](https://user-images.githubusercontent.com/76020485/109002024-021f5f00-76e9-11eb-9902-e74ad82a0628.PNG)

## 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함

시나리오는 단말앱(order)-->재고(stock) 호출 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 재고사용이 과도할 경우 CB 를 통하여 장애격리.

- Hystrix 를 설정:  요청처리 쓰레드에서 처리시간이 310 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정
```
# application.yml

feign:
  hystrix:
    enabled: true 

hystrix:
  command:
    default:
      execution:
        isolation:
          strategy: THREAD
          thread:
            timeoutInMilliseconds: 610         #설정 시간동안 처리 지연발생시 timeout and 설정한 fallback 로직 수행     
      circuitBreaker:
        requestVolumeThreshold: 20           # 설정수 값만큼 요청이 들어온 경우만 circut open 여부 결정 함
        errorThresholdPercentage: 30        # requestVolumn값을 넘는 요청 중 설정 값이상 비율이 에러인 경우 circuit open
        sleepWindowInMilliseconds: 5000    # 한번 오픈되면 얼마나 오픈할 것인지 
      metrics:
        rollingStats:
          timeInMilliseconds: 10000   

```

- 피호출 서비스(재고:stock) 의 사용 호출시의 부하 처리 - 400ms + 220*random ms로 딜레이
```
  # Stock.java 
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
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시
```

root@siege-5b99b44c9c-ldf2l:/# siege -v -c100 -t60s --content-type "application/json" 'http://order:8080/orders POST {"phoneNumber":"01087654321", "productName":"coffee", "qty":2, "amt":1000}'
** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...

....


HTTP/1.1 500     2.79 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.93 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.46 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.39 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.27 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.60 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.82 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.93 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     0.76 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.43 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.39 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.30 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.83 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.30 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.30 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.80 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.81 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     1.66 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     2.05 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     2.30 secs:     248 bytes ==> POST http://order:8080/orders
HTTP/1.1 500     2.57 secs:     248 bytes ==> POST http://order:8080/orders
siege aborted due to excessive socket failure; you
can change the failure threshold in $HOME/.siegerc

Transactions:                    640 hits
Availability:                  36.30 %
Elapsed time:                  45.01 secs
Data transferred:               0.46 MB
Response time:                  6.99 secs
Transaction rate:              14.22 trans/sec
Throughput:                     0.01 MB/sec
Concurrency:                   99.43
Successful transactions:         640
Failed transactions:            1123
Longest transaction:           11.25
Shortest transaction:           0.09
```
- order 서비스의 로그를 확인하여 Circuit이 OPEN된 것을 확인한다.
```
ERROR 1 --- [o-8080-exec-172] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed; nested exception is org.springframework.transaction.TransactionSystemException: Could not commit JPA transaction; nested exception is javax.persistence.RollbackException: Error while committing the transaction] with root cause

java.lang.RuntimeException: Hystrix circuit short-circuited and is OPEN
        at com.netflix.hystrix.AbstractCommand.handleShortCircuitViaFallback(AbstractCommand.java:979) ~[hystrix-core-1.5.18.jar!/:1.5.18]
        at com.netflix.hystrix.AbstractCommand.applyHystrixSemantics(AbstractCommand.java:557) ~[hystrix-core-1.5.18.jar!/:1.5.18]
        at com.netflix.hystrix.AbstractCommand.access$200(AbstractCommand.java:60) ~[hystrix-core-1.5.18.jar!/:1.5.18]
        at com.netflix.hystrix.AbstractCommand$4.call(AbstractCommand.java:419) ~[hystrix-core-1.5.18.jar!/:1.5.18]
        at com.netflix.hystrix.AbstractCommand$4.call(AbstractCommand.java:413) ~[hystrix-core-1.5.18.jar!/:1.5.18]
        at rx.internal.operators.OnSubscribeDefer.call(OnSubscribeDefer.java:46) ~[rxjava-1.3.8.jar!/:1.3.8]
        at rx.internal.operators.OnSubscribeDefer.call(OnSubscribeDefer.java:35) ~[rxjava-1.3.8.jar!/:1.3.8]
        at rx.Observable.unsafeSubscribe(Observable.java:10327) ~[rxjava-1.3.8.jar!/:1.3.8]

```

- 운영시스템은 죽지 않고 지속적으로 CB 에 의하여 적절히 회로가 열림과 닫힘이 벌어지면서 자원을 보호하고 있음을 보여줌. 하지만, 36%의 Availability는 사용성에 있어 좋지 않기 때문에 동적 Scale out (replica의 자동적 추가,HPA) 을 통하여 시스템을 확장 해주는 후속처리가 필요.

### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 


- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다:
```
$ kubectl get pods
NAME                              READY   STATUS    RESTARTS   AGE
customercenter-7f57cf5f9f-csp2b   1/1     Running   1          20h
drink-7cb565cb4-d2vwb             1/1     Running   0          37m
gateway-5dd866cbb6-czww9          1/1     Running   0          3d1h
order-595c9b45b9-xppbf            1/1     Running   0          36m
payment-698bfbdf7f-vp5ft          1/1     Running   0          2m32s
siege-5b99b44c9c-8qtpd            1/1     Running   0          3d1h


$ kubectl autoscale deploy payment --min=1 --max=10 --cpu-percent=15
horizontalpodautoscaler.autoscaling/payment autoscaled

$ kubectl get hpa
NAME      REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
payment   Deployment/payment   2%/15%    1         10        1          2m35s

# CB 에서 했던 방식대로 워크로드를 1분 동안 걸어준다.

root@siege-5b99b44c9c-ldf2l:/# siege -v -c100 -t60s --content-type "application/json" 'http://order:8080/orders POST {"phoneNumber":"01087654321", "productName":"coffee", "qty":2, "amt":1000}'
** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...

$ kubectl get pods
NAME                              READY     STATUS    RESTARTS   AGE
customercenter-59f4d6d897-lnpsh   1/1       Running   0          97m
drink-64bc64d49c-sdwlb            1/1       Running   0          112m
gateway-6dcdf4cb9-pghzz           1/1       Running   0          74m
order-7ff9b5458-4wn28             1/1       Running   2          21m
payment-6f75856f77-b6ctw          1/1       Running   0          118s
payment-6f75856f77-f2l5m          1/1       Running   0          102s
payment-6f75856f77-gl24n          1/1       Running   0          41m
payment-6f75856f77-htkn5          1/1       Running   0          118s
payment-6f75856f77-rplpb          1/1       Running   0          118s
siege-5b99b44c9c-ldf2l            1/1       Running   0          96m
```

- HPA를 확인한다.
```
$ kubectl get hpa 
NAME      REFERENCE            TARGETS   MINPODS   MAXPODS   REPLICAS   AGE
payment   Deployment/payment   72%/15%   1         10        5          12m
```

- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy payment -w
```
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다:
```
NAME      DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
payment   1         1         1         1         1h
payment   4         1         1         1         1h
payment   4         1         1         1         1h
payment   4         1         1         1         1h
payment   4         4         4         1         1h
payment   5         4         4         1         1h
payment   5         4         4         1         1h
payment   5         4         4         1         1h
payment   5         5         5         1         1h

# siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 

Transactions:		         900 hits
Availability:		       76.08 %
Elapsed time:		       59.33 secs
Data transferred:	        0.34 MB
Response time:		        6.14 secs
Transaction rate:	       15.17 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       93.08
Successful transactions:         900
Failed transactions:	         283
Longest transaction:	       14.41
Shortest transaction:	        0.04

```


## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
# siege -v -c100 -t60s --content-type "application/json" 'http://order:8080/orders POST {"phoneNumber":"01087654321", "productName":"coffee", "qty":2, "amt":1000}'
** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...
HTTP/1.1 201     0.20 secs:     321 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.34 secs:     321 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.39 secs:     321 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.38 secs:     321 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.40 secs:     321 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.40 secs:     321 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.40 secs:     321 bytes ==> POST http://order:8080/orders
HTTP/1.1 201     0.41 secs:     321 bytes ==> POST http://order:8080/orders
:

```

- 새버전으로의 배포 시작

```
order version

v1 : default version 
v3 : circuit breaker version 
v4 : default version
v6 : graceful shutdown version
```
- 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행할 수 있기 때문에 이를 막기위해 Readiness Probe 를 설정하여 이미지를 배포
```
$ kubectl set image deployment/order order=496278789073.dkr.ecr.ap-northeast-2.amazonaws.com/skteam04/order:v4
deployment.apps/order image updated
```

```
# deployment.yaml 의 readiness probe 의 설정:

kubectl apply -f kubernetes/deployment.yaml
```
- 재배포 한 후 Availability 확인:
```
root@siege-5b99b44c9c-ldf2l:/# siege -v -c100 -t60s --content-type "application/json" 'http://order:8080/orders POST {"phoneNumber":"01087654321", "productName":"coffee", "qty":2, "amt":1000}'
** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...
Lifting the server siege...
Transactions:		        4300 hits
Availability:		       99.79 %
Elapsed time:		       59.08 secs
Data transferred:	        1.33 MB
Response time:		        1.05 secs
Transaction rate:	       72.78 trans/sec
Throughput:		        0.02 MB/sec
Concurrency:		       76.67
Successful transactions:        4300
Failed transactions:	           9
Longest transaction:	        4.07
Shortest transaction:	        0.03
```

배포기간중 Availability 가 99.79% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 기존 서비스의 처리 중 종료했기 때문. 이를 막기위해 Graceful Shutdown을 적용
```
# Graceful Shutdown 적용 
public class TomcatGracefulShutdown implements TomcatConnectorCustomizer, ApplicationListener<ContextClosedEvent> {

	private Integer waiting = 30; 
	
    private volatile Connector connector;

    @Override
    public void customize(Connector connector) {
        this.connector = connector;
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        this.connector.pause();
        Executor executor = this.connector.getProtocolHandler().getExecutor();
        if (executor instanceof ThreadPoolExecutor) {
            try {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                threadPoolExecutor.shutdown();
                if (!threadPoolExecutor.awaitTermination(waiting, TimeUnit.SECONDS)) {
                    log.error("Tomcat thread pool did not shut down gracefully within {} seconds. Proceeding with forceful shutdown", waiting);

                    threadPoolExecutor.shutdownNow();

                    if (!threadPoolExecutor.awaitTermination(waiting, TimeUnit.SECONDS)) {
                        log.error("Tomcat thread pool did not terminate");
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

}
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
root@siege-5b99b44c9c-ldf2l:/# siege -v -c100 -t60s --content-type "application/json" 'http://order:8080/orders POST {"phoneNumber":"01087654321", "productName":"coffee", "qty":2, "amt":1000}'
** SIEGE 4.0.4
** Preparing 100 concurrent users for battle.
The server is now under siege...
Lifting the server siege...
Transactions:		        5261 hits
Availability:		      100.00 %
Elapsed time:		       59.28 secs
Data transferred:	        1.62 MB
Response time:		        1.09 secs
Transaction rate:	       88.75 trans/sec
Throughput:		        0.03 MB/sec
Concurrency:		       97.08
Successful transactions:        5261
Failed transactions:	           0
Longest transaction:	        7.52
Shortest transaction:	        0.01

```

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.

## Persistence Volum Claim
서비스의 log를 persistence volum을 사용하여 재기동후에도 남아 있을 수 있도록 하였다.
```

# application.yml

:
server:
  tomcat:
    accesslog:
      enabled: true
      pattern:  '%h %l %u %t "%r" %s %bbyte %Dms'
    basedir: /logs/drink

logging:
  path: /logs/stock
  file:
    max-history: 30
  level:
    org.springframework.cloud: debug

# deployment.yaml

volumeMounts:
 - name: logs
  mountPath: /logs
  volumes:
   - name: logs
   persistentVolumeClaim:
     claimName: logs

# pvc.yaml

apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: logs
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```
stock deployment를 삭제하고 재기동해도 log는 삭제되지 않는다.

```
$ kubectl delete -f drink/kubernetes/deployment.yml
deployment.apps "stock" deleted

$ kubectl apply -f drink/kubernetes/deployment.yml
deployment.apps/stock created

root@labs-776969070:/home/project/team/cafeteria# kubectl exec -it stock-68d468588d-rcpnm -c stock  -- /bin/sh  
/logs/stock # ls -al
total 2068
drwxr-xr-x    4 root     root          4096 Feb 24 14:09 .
drwxr-xr-x    5 root     root          4096 Feb 24 14:04 ..
drwxr-xr-x    2 root     root          4096 Feb 24 14:04 logs
-rw-r--r--    1 root     root       1446032 Feb 24 14:09 spring.log
-rw-r--r--    1 root     root        226658 Feb 24 14:08 spring.log.2021-02-24.0.gz
-rw-r--r--    1 root     root        209202 Feb 24 14:09 spring.log.2021-02-24.1.gz
-rw-r--r--    1 root     root        206012 Feb 24 14:09 spring.log.2021-02-24.2.gz
drwxr-xr-x    3 root     root          4096 Feb 24 14:04 work
/logs/stock # 
```

## ConfigMap / Secret
mongo db의 database이름과 username, password는 환경변수를 지정해서 사용핳 수 있도록 하였다.
database 이름은 kubernetes의 configmap을 사용하였고 username, password는 secret을 사용하여 지정하였다.

```
# secret 생성
kubectl create secret generic mongodb --from-literal=username=mongodb --from-literal=password=mongodb --namespace cafeteria

# configmap.yaml

apiVersion: v1
kind: ConfigMap
metadata:
  name: mongodb
  namespace: cafeteria
data:
  database: "cafeteria"
  

# application.yml

spring:
  data:
    mongodb:
      uri: mongodb://my-mongodb-0.my-mongodb-headless.mongodb.svc.cluster.local:27017,my-mongodb-1.my-mongodb-headless.mongodb.svc.cluster.local:27017
      database: ${MONGODB_DATABASE}
      username: ${MONGODB_USERNAME}
      password: ${MONGODB_PASSWORD}

#buildspec.yaml
spec:
containers:
  - name: $_PROJECT_NAME
    image: $AWS_ACCOUNT_ID.dkr.ecr.$AWS_DEFAULT_REGION.amazonaws.com/$_PROJECT_NAME:$CODEBUILD_RESOLVED_SOURCE_VERSION
    ports:
    - containerPort: 8080
    env:
    - name: MONGODB_DATABASE
      valueFrom:
	configMapKeyRef:
	  name: mongodb
	  key: database
    - name: MONGODB_USERNAME
      valueFrom:
	secretKeyRef:
	  name: mongodb
	  key: username
    - name: MONGODB_PASSWORD
      valueFrom:
	secretKeyRef:
	  name: mongodb
	  key: password
```

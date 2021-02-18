# cafeteria
# Table of contents

- [음료주문](#---)
  - [서비스 시나리오](#서비스-시나리오)
  - [분석/설계](#분석설계)
  - [구현:](#구현-)
    - [DDD 의 적용](#ddd-의-적용)
    - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
    - [폴리글랏 프로그래밍](#폴리글랏-프로그래밍)
    - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
    - [비동기식 호출 과 Eventual Consistency](#비동기식-호출--시간적-디커플링--장애격리--최종-eventual-일관성-테스트)
  - [운영](#운영)
    - [CI/CD 설정](#cicd-설정)
    - [동기식 호출 / 서킷 브레이킹 / 장애격리](#동기식-호출--서킷-브레이킹--장애격리)
    - [오토스케일 아웃](#오토스케일-아웃)
    - [무정지 재배포](#무정지-재배포)

# 서비스 시나리오

음료주문

기능적 요구사항
1. 고객이 음료를 주문한다
1. 고객이 결제한다
1. 결제가 되면 주문 내역이 바리스타에게 전달된다
1. 바리스타는 주문내역을 확인하여 음료를 접수하고 제조한다.
1. 고객이 주문을 취소할 수 있다
1. 주문이 취소되면 음료를 취소한다.
1. 고객이 주문상태를 중간중간 조회한다.
1. 주문상태가 바뀔 때 마다 카톡으로 알림을 보낸다.

비기능적 요구사항
1. 트랜잭션
    1. 결제가 되지 않은 주문건은 아예 거래가 성립되지 않아야 한다  Sync 호출
1. 장애격리
    1. 음료제조 기능이 수행되지 않더라도 주문은 받을 수 있어야 한다  Async (event-driven), Eventual Consistency
    1. 결제시스템이 과중되면 사용자를 잠시동안 받지 않고 결제를 잠시후에 하도록 유도한다  Circuit breaker, fallback
1. 성능
    1. 고객이 자주 확인할 수 있는 주문상태를 마이페이지(프론트엔드)에서 확인할 수 있어야 한다  CQRS
    1. 주문상태가 바뀔때마다 카톡 등으로 알림을 줄 수 있어야 한다  Event driven
# 분석설계
1. Event Storming 모델
![image](https://user-images.githubusercontent.com/75828964/106757223-70857a00-6673-11eb-85dc-35cc0bfd7206.png)

개인과제용
![image]https://user-images.githubusercontent.com/76020485/108308927-f2d57880-71f3-11eb-87ca-c34466f74319.png)

1. 헥사고날 아키텍처 다이어그램 도출
![image](https://user-images.githubusercontent.com/75828964/106765217-e8f03900-667b-11eb-8f19-10dc4756dc4b.png)
# 구현:

분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 각 BC별로 대변되는 마이크로 서비스들을 스프링부트로 구현하였다. 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각자의 포트넘버는 8081 ~ 808n 이다)

```
cd order
mvn spring-boot:run

cd payment
mvn spring-boot:run 

cd drink
mvn spring-boot:run  

cd customercneter
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
# order 서비스의 주문처리
```
![image](https://user-images.githubusercontent.com/75828964/106757723-0e794480-6674-11eb-8c25-54579c0c6a78.png)
```
# drink 서비스의 접수처리
```
![image](https://user-images.githubusercontent.com/75828964/106757953-4ed8c280-6674-11eb-8049-2b16ee71ed47.png)
```
# customercenter 서비스의 상태확인
```
![image](https://user-images.githubusercontent.com/75828964/106758091-7465cc00-6674-11eb-9df8-b93a08da3234.png)

## 폴리글랏 퍼시스턴스

앱프런트 (order) 는 서비스 특성상 많은 사용자의 유입과 상품 정보의 다양한 콘텐츠를 저장해야 하는 특징으로 인해 RDB 보다는 Document DB / NoSQL 계열의 데이터베이스인 Mongo DB 를 사용하기로 하였다. 이를 위해 order 의 선언에는 @Entity 가 아닌 @Document 로 마킹되었으며, 별다른 작업없이 기존의 Entity Pattern 과 Repository Pattern 적용과 데이터베이스 제품의 설정 (application.yml) 만으로 MongoDB 에 부착시켰다
```
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
    - name: SPRING_PROFILES_ACTIVE
      value: "docker"
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

#Mypage.scala

@Document
class Mypage {
  
  @Id
  @BeanProperty
  var id :Long = 0L
  ...
```
MongoDB는 Sequence가 지원되지 않아 별도 Collection을 통해서 id sequence를 생성했다.
```
# DatabseSequence.scala
abstract class Sequence {
  var seq :Long
}
case class InitialSequence(var seq : Long) extends Sequence

@Document(collection = "database_sequences")
class DatabaseSequence extends Sequence {

  
  @BeanProperty
  @Id
  var id: String = null
  
  @BeanProperty
  var seq :Long = 0L

}

# MypageViewHandler.scala

def generateSequence (seqName :String) :Long = {
    val query :Query = new Query(Criteria.where("_id").is(seqName));
    val options :FindAndModifyOptions = new FindAndModifyOptions().returnNew(true).upsert(true)
    val update :Update = new Update().inc("seq",1)
    
    val sequence :Option[DatabaseSequence] = Option(mongoOperations.findAndModify(query, update, options, classOf[DatabaseSequence]))
    sequence.getOrElse(InitialSequence(1L)).seq
}
  
  @StreamListener(KafkaProcessor.INPUT)
  def whenOrdered_then_CREATE_1(@Payload ordered :Ordered) {
    try {
      if (ordered.isMe()) {
        
        val mypage :Mypage = new Mypage()
        mypage.id = generateSequence(Mypage.SEQUENCE_NAME)
	...

#pom.xml
<dependencies>
...
    <dependency>
	<groupId>org.springframework.boot</groupId>
	<artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
...
</dependencies>

```
drink 서비스는 maria DB에 익숙한 개발자가 많아 maria DB를 사용하기로 하였다. 이를 위해 order는 별다른 작업없이 기존의 Entity Pattern 과 Repository Pattern 적용과 데이터베이스 제품의 설정 (application.yml) 만으로 maria db에 부착시켰다
```
#application.yml
spring:
  datasource:
    url: jdbc:mariadb://my-mariadb-mariadb-galera.mariadb.svc.cluster.local:3306/${MARIADB_DATABASE}
    driver-class-name: org.mariadb.jdbc.Driver
    username: ${MARIADB_USERNAME}
    password: ${MARIADB_PASSWORD} 

#buildspec.yaml
spec:
containers:
- name: $_PROJECT_NAME
  env:
  - name: MARIADB_DATABASE
    valueFrom:
      configMapKeyRef:
	name: mariadb
	key: database
  - name: MARIADB_USERNAME
    valueFrom:
      secretKeyRef:
	name: mariadb
	key: username
  - name: MARIADB_PASSWORD
    valueFrom:
      secretKeyRef:
	name: mariadb
	key: password
	  
#pom.xml
<dependencies>
...
	<dependency>
		<groupId>org.mariadb.jdbc</groupId> 
		<artifactId>mariadb-java-client</artifactId> 
	</dependency>
...
</dependencies>



```
## 폴리글랏 프로그래밍

고객관리 서비스(customercenter)의 시나리오인 주문상태 변경에 따라 고객에게 카톡메시지 보내는 기능의 구현 파트는 해당 팀이 scala를 이용하여 구현하기로 하였다. 해당 Scala 구현체는 각 이벤트를 수신하여 처리하는 Kafka consumer 로 구현되었고 코드는 다음과 같다:
```
import org.springframework.messaging.SubscribableChannel
import org.springframework.cloud.stream.annotation.Output
import org.springframework.cloud.stream.annotation.Input
import org.springframework.messaging.MessageChannel

object KafkaProcessor {
  final val INPUT = "event-in"
  final val OUTPUT = "event-out"
}

trait KafkaProcessor {

  @Input(KafkaProcessor.INPUT)
  def inboundTopic() :SubscribableChannel

  @Output(KafkaProcessor.OUTPUT)
  def outboundTopic() :MessageChannel
}

 # 카톡호출 API
import org.springframework.stereotype.Component

@Component
class KakaoServiceImpl extends KakaoService {
  
	override def sendKakao(message :KakaoMessage) {
		logger.info(s"\nTo. ${message.phoneNumber}\n${message.message}\n")
	}
}

```

## 동기식 호출 과 Fallback 처리

분석단계에서의 조건 중 하나로 주문(order)->결제(payment) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 이미 앞서 Rest Repository 에 의해 노출되어있는 REST 서비스를 FeignClient 를 이용하여 호출하도록 한다. 

- 결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현 

```
# (payment) PaymentService.java

package cafeteria.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name="payment", url="${feign.client.payment.url}")
public interface PaymentService {

    @RequestMapping(method= RequestMethod.POST, path="/payments")
    public void pay(@RequestBody Payment payment);

}
```

- 주문을 받은 직후(@PostPersist) 결제를 요청하도록 처리
```
# Order.java (Entity)

    @PostPersist
    public void onPostPersist(){
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.publishAfterCommit();

        Payment payment = new Payment();
        payment.setOrderId(this.id);
        payment.setPhoneNumber(this.phoneNumber);
        payment.setAmt(this.amt);
        
        OrderApplication.applicationContext.getBean(PaymentService.class).pay(payment);


    }
```

- 동기식 호출에서는 호출 시간에 따른 타임 커플링이 발생하며, 결제 시스템이 장애가 나면 주문도 못받는다는 것을 확인:


```
# 결제 (payment) 서비스를 잠시 내려놓음
$ kubectl delete deploy payment
deployment.apps "payment" deleted

#주문처리
```
![image](https://user-images.githubusercontent.com/75828964/106758360-c4449300-6674-11eb-9d9c-4055219c3612.png)
```
#결제서비스 재기동
$ kubectl apply -f deployment.yml
deployment.apps/payment created

#주문처리
```
![image](https://user-images.githubusercontent.com/75828964/106758565-0077f380-6675-11eb-9c61-c13da5183897.png)

- 또한 과도한 요청시에 서비스 장애가 도미노 처럼 벌어질 수 있다. (서킷브레이커, 폴백 처리는 운영단계에서 설명한다.)



## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트

결제가 이루어진 후에 상점시스템으로 이를 알려주는 행위는 동기식이 아니라 비 동기식으로 처리하여 상점 시스템의 처리를 위하여 결제주문이 블로킹 되지 않아도록 처리한다.
 
- 이를 위하여 결제이력에 기록을 남긴 후에 곧바로 결제승인이 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
 
```
package cafeteria;

@Entity
@Table(name="Payment")
public class Payment {

 ...
    @PostPersist
    public void onPostPersist(){
        PaymentApproved paymentApproved = new PaymentApproved();
        BeanUtils.copyProperties(this, paymentApproved);
        paymentApproved.publishAfterCommit();

    }

}
```
- 음료 서비스에서는 결제승인 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:

```
package cafeteria;

...

@StreamListener(KafkaProcessor.INPUT)
    public void wheneverPaymentApproved_(@Payload PaymentApproved paymentApproved){

        if(paymentApproved.isMe()){
            System.out.println("##### listener  : " + paymentApproved.toJson());
            
            Drink drink = new Drink();
            drink.setOrderId(paymentApproved.getOrderId());
            drink.setStatus(paymentApproved.getStatus());
            drinkRepository.save(drink);
            
        }
    }

```
실제 구현에서 카톡은 화면에 출력으로 대체하였다.
  
```    
  @StreamListener(KafkaProcessor.INPUT)
  def whenReceipted_then_UPDATE_3(@Payload made :Made) {
    try {
      if (made.isMe()) {
        
        val message :KakaoMessage = new KakaoMessage()
        message.phoneNumber = made.phoneNumber
        message.message = s"""Your Order is ${made.status}\nCome and Take it, Please!"""
        kakaoService.sendKakao(message)
      }
    } catch {
      case e :Exception => e.printStackTrace()
    }
  }

@Component
class KakaoServiceImpl extends KakaoService {
  
	override def sendKakao(message :KakaoMessage) {
		logger.info(s"\nTo. ${message.phoneNumber}\n${message.message}\n")
	}
}


```

음료 시스템은 주문/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 음료시스템이 유지보수로 인해 잠시 내려간 상태라도 주문을 받는데 문제가 없다:
```
# 음료 서비스 (drink) 를 잠시 내려놓음
$ kubectl delete deploy drink
deployment.apps "drink" deleted

#주문처리
```
![image](https://user-images.githubusercontent.com/75828964/106759046-9ca1fa80-6675-11eb-9782-9ba234c5747a.png)
```
#음료 서비스 기동
kubectl apply -f deployment.yml
deployment.apps/drink created

#음료등록 확인
```
![image](https://user-images.githubusercontent.com/75828964/106759161-c2c79a80-6675-11eb-9e08-cf98ec5b4fc2.png)



# 운영

## CI/CD 설정


각 구현체들은 각자의 source repository 에 구성되었고, 사용한 CI/CD 플랫폼은 AWS를 사용하였으며, pipeline build script 는 각 프로젝트 폴더 이하에 buildspec.yml 에 포함되었다.


## 동기식 호출 / 서킷 브레이킹 / 장애격리

* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함

시나리오는 단말앱(order)-->결제(payment) 시의 연결을 RESTful Request/Response 로 연동하여 구현이 되어있고, 결제 요청이 과도할 경우 CB 를 통하여 장애격리.

- Hystrix 를 설정:  요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정
```
# application.yml

hystrix:
  command:
    # 전역설정
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610

```

- 피호출 서비스(결제:payment) 의 임의 부하 처리 - 400 밀리에서 증감 220 밀리 정도 왔다갔다 하게
```
# (payment) Payment.java (Entity)

    @PrePersist
    public void onPrePersist(){  //결제이력을 저장한 후 적당한 시간 끌기

        ...
        
        try {
            Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인:
- 동시사용자 100명
- 60초 동안 실시

![image](https://user-images.githubusercontent.com/75828964/106759329-f9051a00-6675-11eb-93fa-daf7924d5718.png)
![image](https://user-images.githubusercontent.com/75828964/106759337-fd313780-6675-11eb-90ac-e62f5fbc6648.png)

- 운영시스템은 죽지 않고 지속적으로 CB 에 의하여 적절히 회로가 열림과 닫힘이 벌어지면서 자원을 보호하고 있음을 보여줌. 하지만, 63.55% 가 성공하였고, 46%가 실패했다는 것은 고객 사용성에 있어 좋지 않기 때문에 Retry 설정과 동적 Scale out (replica의 자동적 추가,HPA) 을 통하여 시스템을 확장 해주는 후속처리가 필요.

- Retry 의 설정 (istio)
- Availability 가 높아진 것을 확인 (siege)

### 오토스케일 아웃
앞서 CB 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다. 


- 결제서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 15프로를 넘어서면 replica 를 10개까지 늘려준다:
```
kubectl autoscale deploy payment --min=1 --max=10 --cpu-percent=15

kubectl get pods
```
![image](https://user-images.githubusercontent.com/75828964/106759802-80528d80-6676-11eb-9cbe-637498405ca7.png)
```
kubectl get hpa
```
![image](https://user-images.githubusercontent.com/75828964/106759813-834d7e00-6676-11eb-8ba4-c3fe183b91c7.png)
```
- CB 에서 했던 방식대로 워크로드를 2분 동안 걸어준다.
```
![image](https://user-images.githubusercontent.com/75828964/106760580-52217d80-6677-11eb-8e95-2c162d8b8b47.png)


- 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다:
```
kubectl get deploy payment -w
```
- 어느정도 시간이 흐른 후 (약 30초) 스케일 아웃이 벌어지는 것을 확인할 수 있다:
```
NAME        DESIRED   CURRENT   UP-TO-DATE   AVAILABLE   AGE
payment     1         1         1            1           17s
payment     1         2         1            1           45s
payment     1         4         1            1           1m
:

watch kubectl get pods
```
![image](https://user-images.githubusercontent.com/75828964/106760302-ff47c600-6676-11eb-8f6e-d82865e06642.png)

- siege 의 로그를 보아도 전체적인 성공률이 높아진 것을 확인 할 수 있다. 
```
Transactions:		        5078 hits
Availability:		       92.45 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02
```


## 무정지 재배포

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
siege -c100 -t120S -r10 --content-type "application/json" 'localhost:8081/orders POST {"phoneNumber": "01012345678","productName": "coffee","qty": 2,"amt": 1000}'

** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://localhost:8081/orders
:

```

- 새버전으로의 배포 시작
```
kubectl set image ...
```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
```
Transactions:		        3078 hits
Availability:		       70.45 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```
배포기간중 Availability 가 평소 100%에서 70% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:

```
# deployment.yaml 의 readiness probe 의 설정:


kubectl apply -f kubernetes/deployment.yaml
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:
```
Transactions:		        3078 hits
Availability:		       100 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.

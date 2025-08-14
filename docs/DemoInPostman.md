## Postman Demo ##
These are the few issues that I have often observed when running a demo of the cache service in Postman
  - A Signed In Postman user
  - Postman Desktop / Web Application
  - Issues with Environment variables

### Starting with the Postman Demo ###
Pressing the "Run in Postman" button, will take you to the "Fork the collection in your worksapce" screen. (You can try that ...) 

<img width="374" height="323" alt="image" src="https://github.com/user-attachments/assets/822d59e5-90eb-435b-9136-290421ed9e91" />


My suggestion is to press the "View Collection" link that, which will take you the Collection page like this one 

<img width="1845" height="833" alt="image" src="https://github.com/user-attachments/assets/6f73a752-9711-44c2-8444-d4353ff73e49" />

Since the Web Demo works in cluster mode to demostrate the distributed servers, CH algo, etc., open the folder "Cluster" and have an initial feel of the APIs in them

<img width="150" height="200" alt="image" src="https://github.com/user-attachments/assets/689d1fac-920a-4926-8d4b-eaa34145b4d3" />

At this point, you should be Signed In. To do this you should see a "Sign In" link at top right corner. **Pls do NOTE that if you don't Sign In to postman, __it will not allow you to make API requests__"

<img width="500" height="30" alt="image" src="https://github.com/user-attachments/assets/fc9be33c-b62a-4b3e-bfc1-6b00319fd086" />



### Environment setting and Issues ###
The next step is to set the environment, you can do this from the top right corner, just below the Sign In section

<img width="300" height="170" alt="image" src="https://github.com/user-attachments/assets/a20bc744-e484-4dff-926e-9e760ffa60c6" />

At this point, choose the __PRODUCTION__ environment

In the left hand navigation bar, you will see two tabs : "Collections" and "Environments", choose the Environment tab. You will see three options, choose the PRODUCTION option, this will take u to the Production environment variables settings

<img width="310" height="220" alt="image" src="https://github.com/user-attachments/assets/ac1c7b59-a900-4a54-98b3-5906dabda698" />

At this point, please ensure that youc can see the "Initial Values" in the Production environment, and not blanks.

<img width="600" height="155" alt="image" src="https://github.com/user-attachments/assets/31a7be13-fb2f-4f8f-9154-ac27c145defe" />

In the image above, you will see garbage values in "Current Values" (in this case 2.0.0.0 for node2 parameter). In case you are seeing something like this click the three dots on the right and "Reset All" and then Save 

<img width="400" height="190" alt="image" src="https://github.com/user-attachments/assets/7143e359-7175-4ce8-897f-5a473fd61323" />


At this point, I also recommend that you use the Postman Desktop App, in case you have decided to keep using the Postman Web App, then please power through below ... I am assuming you are already signed in at this stage.



## Postman Demo ##
These are the few issues that I have often observed when running a demo of the cache service in Postman
  - Initial steps ... View Collection, Cluster folder
  - Issue with "Guest" users
  - Issues with Environment variables
  - Issues with Postman Web App

I know this is a bit cumbersome for a demo, but bear with me and I will solve this soon.

### Initial Steps ... View Collection, Cluster folder ###
On pressing the "Run in Postman" button, will take you to the "Fork the collection in your worksapce" screen. (You can try that ... at your expense :) ) 

<img width="374" height="323" alt="image" src="https://github.com/user-attachments/assets/822d59e5-90eb-435b-9136-290421ed9e91" />


My suggestion is to press the "View Collection" link that, which will take you the Collection page like this one 

<img width="1845" height="833" alt="image" src="https://github.com/user-attachments/assets/6f73a752-9711-44c2-8444-d4353ff73e49" />

Since the Web Demo works in cluster mode to demostrate the distributed servers, CH algo, etc., next step is to verify the folder "Cluster" and have an initial feel of the APIs in them

<img width="150" height="200" alt="image" src="https://github.com/user-attachments/assets/689d1fac-920a-4926-8d4b-eaa34145b4d3" />


### Issue with "Guest" users ###

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



**Next step is to choose the Postman Desktop or Web Application. I suggest that you you go with the Desktop App. In case you decide to keep using the Web App read through the next section**

### Issues with Postman Web App ###

The issue with Postman Web app is primarily a CORS related issue. To work around this Postman has provided a couple of agents 
  - Cloud Agent
  - Browser Agent

You can see the Agents at the bottom of the browser right hand side (I'm assuming you are signed in)

<img width="450" height="43" alt="image" src="https://github.com/user-attachments/assets/b5017f76-907a-4a91-b53b-b39fd8639d08" />

Click on the "Browser / Cloud Agent", which will open a multi select user interface. Ensure that Cloud Agent is selected

<img width="250" height="276" alt="image" src="https://github.com/user-attachments/assets/913b51c4-219d-4b03-9567-43510fc44ef6" />

By this time, you should be able to test APIs

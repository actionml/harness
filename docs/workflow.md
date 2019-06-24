# Workflow

To Use an Engine Instance create one, use it then delete it when it is not needed. Harness workflow for any Engine Instance proceeds from input to query/prediction.

The format for input Events and queries is Engine dependent so see the Engine documentation. 

 - Start the Harness server after the component services like Spark, DBs, etc:
        
        harness start 
         
 - Create a new Engine Instance and set it's configuration parameters:

        harness add </path/to/some-engine.json>
        # the engine-id in the JSON file will be used for the resource-id
                
 - The end user sends events to the engine-id using the SDK and the engine stores input.    
        
![](https://docs.google.com/drawings/d/e/2PACX-1vQNmHQRJXQq4GAQFxA2_8O4U6_XCXOfFa8i89H0Uyy3SXLo2ePxrnzewJhDW-CanGbz5ivSlo91wcmn/pub?w=1180&h=572)

 - For a Lambda style (batch/background) learner run:
    
        harness train <some-engine-id>
        # creates or updates the engine's model
        

    Once the Engine Instance has input it can train it can train at any time without disrupting input or queries.

![](https://docs.google.com/drawings/d/e/2PACX-1vTU8JJgRzfIawtzJW03SAmaf2lQiaFVbbPox19WJnyefXEmEn-P7ghHWhNZB9OIIL-DIw4oEZsES1Iq/pub?w=1180&h=572)

 - Once the Engine has created or updated its model Harness will respond to queries at any time.

![](https://docs.google.com/drawings/d/e/2PACX-1vS7BAt8974bgFtS0Do0qwn15WhhopBABKcSPVlbe-krMT4Ky49tJQT9OWuq2Zp9KX0JwAResMJshr9O/pub?w=1180&h=572)
    
 - If you wish to **remove all data** and the engine to start fresh:

        harness delete <some-engine-id>

 - To bring the server down:

        harness stop
        
## Enabling Auth

See instructions in [Security](security.md)
 
## Workflow With Auth   

Once Auth is enabled a User must have Role with permission to access one or more engine-ids via the Events and Queries resource types. The Admin User runs the CLI and has access to all resources.

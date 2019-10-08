# Contextual Bandit Algorithm
                   
At a high level, the main advantages of this approach are:

 * Works well with a small number of items & events, as compared with other recommenders which can work well with a small or large number of items, but work best with a large number of events.    
 * Works well when what you really want is the single best recommendation instead     of a ranked or scored list of the best recommendations.
 * Can take into  account context (for example: gender, location, etc.) and therefore allows recommendations to be made for brand new users [cold-start].
 * Has the ability to learn “online”, meaning every event affects the recommendations in real-time, instead of requiring a “train” step. Note that in the first cut of our implementation, this feature is not present, but it can be added later.
 * Can take into account that user behavior can have variable value to the business.     So, can make a recommendation that will result in the highest-revenue conversion rather than just one that will result in a conversion. Note that in the first cut of our implementation, this feature is not present, but it can be added later.

The Contextual Bandit (CB) uses a close relative of an algorithm known as Contextual Bandit, a solution to the [Multi-Armed Bandit problem](https://en.wikipedia.org/wiki/Multi-armed_bandit) which can use context (additional information). 
Specifically, we use Cost-Sensitive One-against-all Classification, with the additional of sampling. 
Put simply, the algorithm considers a number of actions (variants) which can be taken at any point in time. In the beginning, we do not know which of these actions will result in our desired outcome (conversion). 
So, initially, the algorithm will randomly (uniformly) recommend all of the variants it is aware of.

As we begin to use those recommendations to expose variants to users, we see how the users reacts to them. This information is fed back into the recommender in the form of events. In the simplest form, the algorithm is “greedy”, in the sense that it will pick a single best action to take each time we request a recommendation. However, this would not be good, because we would always make the same recommendation, and only ever get information about how the user reacts to it, and not to the other possible choices.

Instead, we would like to “explore” all of the options by exposing users to them. So, we give the “greedy” choice the highest probability, and reserve the rest of the probability to spread evenly across the other options. As time passes and we get more events / information, then the “greedy” choice will get a higher and higher probability and the rest of the choices will get a lower probability. This reflects the fact that we are more confident in our recommendations the more events we have seen.

This approach means that eventually, the algorithm will recommend only the best variant for a given test-group and user/context. This is desirable behavior so long as the tests last for a limited amount of time (which is expected). If we did not have limited tests, but instead were using this algorithm more generally to recommend to all users over all time, we might want to alter this behavior to account for the fact that user preferences might change over time, and we would need to continue to “explore” as things change.

References:
[A contextual-bandit algorithm for mobile context-aware recommender system] (http://www.researchgate.net/profile/Djallel_Bouneffouf/publication/235683567_A_Contextual_Bandit_Algorithm_for_Mobile_Context-Aware_Recommender_System/links/09e41513cc887dd2c9000000.pdf) - D Bouneffouf, A Bouzeghoub, AL Gançarski

[Doubly robust policy evaluation and learning](http://arxiv.org/pdf/1103.4601) - M Dudík, J Langford, L Li

[The Epoch-Greedy Algorithm for Multi-armed Bandits with Side Information] (http://papers.nips.cc/paper/3178-the-epoch-greedy-algorithm-for-multi-armed-bandits-with-side-information.pdf) - J Langford, T Zhang

[Vowpal Wabbit Contextual Bandit Example](https://github.com/JohnLangford/vowpal_wabbit/wiki/Contextual-Bandit-Example)

[A Multiworld Testing Decision Service](https://arxiv.org/pdf/1606.03966v1.pdf) - J Langford et al

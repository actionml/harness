# Kappa Learning

We treat input as a time ordered sequence of events, with source timestamps. These arrive to the learner nearly in order but no effort is paid to enforce the ordering other than time stamping when they arrive to the learner.

Key Concepts:

 - **time window**: A fixed interval of time, usually "now" back in time some fixed amount.
 - **dataset**: The *dataset* consists of a *time window* of events This is a moving time window in the sense that now moves forward and the last event is always a fixed time in the past.
 - **watermark**: For an online learning algorithm in PIO-Kappa the *watermark* is the model as last calculated and persisted. It is the actual model timestamped with the arrival timestamp (in processing time) of the last event that was used in its calculation. If the online learner is interrupted the model will be restored to the last *watermark* and any newer events will be used to update the model, on the way creating new *watermarks*
 - **model**: The product of an algorithm that consumes events. The model is queried for Machine Learning results. A result of the query for a recommender is recommendations, for a classifier is the classification of a piece of data. The key difference in a Kappa style learner vs Lambda style is that the model is nearly up-to-date with real time (there may be a small difference but it is a design point to minimize this), whereas the Lambda model is only up-to-date with its last background recalculation&mdash;though queries may use real time data to return model based results.

# Benefits

Neither Kappa nor Lambda should be considered "best". They both have benefits.

 - **Lambda**: For complex model calculations, training on a large batch of data may be much simpler than recalculating with each new event. This method often build more complex models than are possible with *kappa*.
 - **Kappa**: When it is possible to calculate incremental updates to a model in roughly the same time it takes to receive the next event, *kappa* benefits apply. With Kappa models, it is only necessary to store datasets that have events from the last watermark to the current time. This hugely reduces the data persistence needs. It is often valuable to store some time window of data in case parameters change and a new model is needed from the same events so this benefit has caveats.

# References

 - [Google Research Paper on "The Dataflow Model"](https://static.googleusercontent.com/media/research.google.com/en//pubs/archive/43864.pdf)
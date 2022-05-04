# CordonBleuAdapterDelegate

### Import:
```
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```
```
dependencies {
    implementation 'com.github.dashkudik:CordonBleuAdapterDelegate:1.2-beta'
}
```
### Example:
```
private val adapter by cordonBleuAdapter<SomeDataModel>()
        .diffUtil(diffUtil)
        .pageSize(30)
        .enableAutomaticLoadingRetrying(3000) // If error occurred
        .prefetchDistance(10)
        .onPortionLoading { numberOfRequiredPage, offset ->  
            // Something boring
        }.onPortionLoaded { throwable, numberOfRequiredPage, offset -> 
            // Something boring
        }.onPortionRequired { numberOfRequiredPage, offset ->
            // Suspend call which provides a portion
        }.viewHolder(
            viewType = 1,
            layoutId = R.layout.some_layout,
            binder = SomeLayoutBinding::bind,
            onBind = { model, position ->
               // Binding receiver here
            },
            clicks = arrayOf(
                R.id.someId click {
                    // Something interesting
                }
            )
        ).viewHolder(
            viewType = 2,
            layoutId = R.layout.another_layout,
            binder = AnotherLayoutBinding::bind,
            onBind = { model, position ->
               // Binding receiver here
            },
            clicks = arrayOf(
                R.id.anotherId click {
                    // Something interesting
                }
            )
        )
```

### Why?

Сonvenience.

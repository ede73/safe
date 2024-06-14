package fi.iki.ede.safe.splits

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import java.util.ServiceLoader


fun getDFMs(context: Context) = SplitInstallManagerFactory.create(context)
    .installedModules

fun isDFMAvailable(context: Context, moduleName: String) =
    getDFMs(context).contains(moduleName)

//// Replace 'moduleName' with your actual module name
//// Replace 'functionToInvoke' with the function you want to call
//val splitInstallManager = SplitInstallManagerFactory.create(context)
//val request = SplitInstallRequest.newBuilder()
//    .addModule(moduleName)
//    .build()
//
//splitInstallManager.startInstall(request)
//.addOnSuccessListener { sessionId ->
//    // Module downloaded and installed successfully, now invoke the function
//    val moduleClass = Class.forName("$moduleName.ModuleClass") // Adjust with your module's class name
//    val method = moduleClass.getDeclaredMethod("functionToInvoke") // Adjust with the actual function name
//    method.invoke(moduleClass.newInstance())
//}
//.addOnFailureListener { exception ->
//    // Handle the error
//}


//val splitInstallManager = SplitInstallManagerFactory.create(context)
//val listener = SplitInstallStateUpdatedListener { state ->
//    if (state.moduleNames().contains("SpiderMan") && state.status() == SplitInstallSessionStatus.INSTALLED) {
//        // The DFM is installed, perform the initialization here
//        initializeSpiderManModule()
//    }
//}
//
//splitInstallManager.registerListener(listener)
//
//// Don't forget to unregister the listener when it's no longer needed
//splitInstallManager.unregisterListener(listener)

fun registerDFM(context: Context, moduleName: String) {
    // https://github.com/googlesamples/android-dynamic-code-loading/blob/master/app/src/reflect/java/com/google/android/samples/dynamiccodeloading/MainViewModel.kt
    // also "reflection" isn't too hard here
    getDFMs(context).forEach {
        println(it)
    }
//    val PROVIDER_CLASS = "fi.iki.ede.categorypager.RegistrationAPIProviderImpl"
//    val storageModuleProvider2 =
//        Class.forName(PROVIDER_CLASS).kotlin.objectInstance
//    val storageModuleProvider =
//        storageModuleProvider2 as RegistrationAPI.Provider
//
    // https://github.com/googlesamples/android-dynamic-code-loading/blob/master/app/src/serviceLoader/java/com/google/android/samples/dynamiccodeloading/MainViewModel.kt
    // pass classloader and explicitly use iterator to enable R8 optimizations
    val serviceLoader = ServiceLoader.load(
        RegistrationAPI.Provider::class.java,
        RegistrationAPI.Provider::class.java.classLoader
    )
    require(serviceLoader != null) { "Did not get service loader" }
    val iterator = serviceLoader.iterator()
    if (!iterator.hasNext()) {
        println("There is NO next iterator available!?!?")
        return
    }
    val next = iterator.next()
    val module = next.get()
    module.register(context)
}

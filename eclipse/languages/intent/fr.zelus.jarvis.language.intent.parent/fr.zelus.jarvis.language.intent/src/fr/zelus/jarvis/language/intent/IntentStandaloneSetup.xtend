/*
 * generated by Xtext 2.12.0
 */
package fr.zelus.jarvis.language.intent


/**
 * Initialization support for running Xtext languages without Equinox extension registry.
 */
class IntentStandaloneSetup extends IntentStandaloneSetupGenerated {

	def static void doSetup() {
		new IntentStandaloneSetup().createInjectorAndDoEMFRegistration()
	}
}

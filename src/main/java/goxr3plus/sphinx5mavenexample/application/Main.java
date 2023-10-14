package main.java.goxr3plus.sphinx5mavenexample.application;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Port;

import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.LiveSpeechRecognizer;
import edu.cmu.sphinx.api.SpeechResult;
import edu.cmu.sphinx.result.WordResult;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.shape.Box;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class Main extends Application
{
	// Boxes
	private List<Rectangle> rectangles = new LinkedList<>();
	// Necessary
	private LiveSpeechRecognizer recognizer;

	//Root
	private Parent root;

	// Logger
	private Logger logger = Logger.getLogger(getClass().getName());

	/**
	 * This String contains the Result that is coming back from SpeechRecognizer
	 */
	private String speechRecognitionResult;

	//-----------------Lock Variables-----------------------------

	/**
	 * This variable is used to ignore the results of speech recognition cause actually it can't be stopped...
	 *
	 * <br>
	 * Check this link for more information: <a href=
	 * "https://sourceforge.net/p/cmusphinx/discussion/sphinx4/thread/3875fc39/">https://sourceforge.net/p/cmusphinx/discussion/sphinx4/thread/3875fc39/</a>
	 */
	private boolean ignoreSpeechRecognitionResults = false;

	/**
	 * Checks if the speech recognise is already running
	 */
	private boolean speechRecognizerThreadRunning = false;

	/**
	 * Checks if the resources Thread is already running
	 */
	private boolean resourcesThreadRunning;

	//---

	/**
	 * This executor service is used in order the playerState events to be executed in an order
	 */
	private ExecutorService eventsExecutorService = Executors.newFixedThreadPool(2);

	//------------------------------------------------------------------------------------

	/**
	 * Constructor
	 */
	public Main() {

		// Loading Message
		logger.log(Level.INFO, "Loading Speech Recognizer...\n");

		// Configuration
		Configuration configuration = new Configuration();

		// Load model from the jar
		configuration.setAcousticModelPath("resource:/edu/cmu/sphinx/models/en-us/en-us");
		configuration.setDictionaryPath("resource:/edu/cmu/sphinx/models/en-us/cmudict-en-us.dict");

		//====================================================================================
		//=====================READ THIS!!!===============================================
		//Uncomment this line of code if you want the recognizer to recognize every word of the language
		//you are using , here it is English for example
		//====================================================================================
		//configuration.setLanguageModelPath("resource:/edu/cmu/sphinx/models/en-us/en-us.lm.bin");

		//====================================================================================
		//=====================READ THIS!!!===============================================
		//If you don't want to use a grammar file comment below 3 lines and uncomment the above line for language model
		//====================================================================================

		// Grammar
		configuration.setGrammarPath("resource:/grammars");
		configuration.setGrammarName("grammar");
		configuration.setUseGrammar(true);

		try {
			recognizer = new LiveSpeechRecognizer(configuration);
		} catch (IOException ex) {
			logger.log(Level.SEVERE, null, ex);
		}

		// Start recognition process pruning previously cached data.
		// recognizer.startRecognition(true);

		//Check if needed resources are available
		startResourcesThread();
		//Start speech recognition thread
		startSpeechRecognition();
	}

	@Override
	public void start(Stage stage) throws Exception {
		this.root = FXMLLoader.load(getClass().getResource("/fxml/window.fxml"));
		stage.setTitle("Light Center");
		stage.setScene(new Scene(root));
		stage.show();
		System.out.println("All lights available [id]:");
		for(Node n : root.lookupAll(".color-rect"))
		{
			Rectangle b = (Rectangle)n;
			System.out.printf("-%s%n",b.getId());
			rectangles.add(b);

		}

	}

	//-----------------------------------------------------------------------------------------------

	/**
	 * Starts the Speech Recognition Thread
	 */
	public synchronized void startSpeechRecognition() {

		//Check lock
		if (speechRecognizerThreadRunning)
			logger.log(Level.INFO, "Speech Recognition Thread already running...\n");
		else
			//Submit to ExecutorService
			eventsExecutorService.submit(() -> {

				//locks
				speechRecognizerThreadRunning = true;
				ignoreSpeechRecognitionResults = false;

				//Start Recognition
				recognizer.startRecognition(true);

				//Information
				logger.log(Level.INFO, "You can start to speak...\n");

				try {
					while (speechRecognizerThreadRunning) {
						/*
						 * This method will return when the end of speech is reached. Note that the end pointer will determine the end of speech.
						 */
						SpeechResult speechResult = recognizer.getResult();

						//Check if we ignore the speech recognition results
						if (!ignoreSpeechRecognitionResults) {

							//Check the result
							if (speechResult == null)
								logger.log(Level.INFO, "I can't understand what you said.\n");
							else {

								//Get the hypothesis
								speechRecognitionResult = speechResult.getHypothesis();

								//You said?
								System.out.println("You said: [" + speechRecognitionResult + "]\n");

								//Call the appropriate method
								makeDecision(speechRecognitionResult, speechResult.getWords());

							}
						} else
							logger.log(Level.INFO, "Ingoring Speech Recognition Results...");

					}
				} catch (Exception ex) {
					logger.log(Level.WARNING, null, ex);
					speechRecognizerThreadRunning = false;
				}

				logger.log(Level.INFO, "SpeechThread has exited...");

			});
	}

	/**
	 * Stops ignoring the results of SpeechRecognition
	 */
	public synchronized void stopIgnoreSpeechRecognitionResults() {

		//Stop ignoring speech recognition results
		ignoreSpeechRecognitionResults = false;
	}

	/**
	 * Ignores the results of SpeechRecognition
	 */
	public synchronized void ignoreSpeechRecognitionResults() {

		//Instead of stopping the speech recognition we are ignoring it's results
		ignoreSpeechRecognitionResults = true;

	}

	//-----------------------------------------------------------------------------------------------

	/**
	 * Starting a Thread that checks if the resources needed to the SpeechRecognition library are available
	 */
	public void startResourcesThread() {

		//Check lock
		if (resourcesThreadRunning)
			logger.log(Level.INFO, "Resources Thread already running...\n");
		else
			//Submit to ExecutorService
			eventsExecutorService.submit(() -> {
				try {

					//Lock
					resourcesThreadRunning = true;

					// Detect if the microphone is available
					while (true) {

						//Is the Microphone Available
						if (!AudioSystem.isLineSupported(Port.Info.MICROPHONE))
							logger.log(Level.INFO, "Microphone is not available.\n");

						// Sleep some period
						Thread.sleep(350);
					}

				} catch (InterruptedException ex) {
					logger.log(Level.WARNING, null, ex);
					resourcesThreadRunning = false;
				}
			});
	}

	private void turnTheLightON(int position)
	{
		rectangles.get(position).setVisible(true);
	}

	private void turnTheLightOFF(int position)
	{
		rectangles.get(position).setVisible(false);
	}

	/**
	 * Takes a decision based on the given result
	 *
	 * @param speechWords
	 */
	public void makeDecision(String speech , List<WordResult> speechWords) {

		System.out.println(speech);
		//System.out.println(speech);
		String[] command = speech.split(" ");

		//((Text)root.lookup(".ext-pane").lookup(".int-pane").lookup(".command-text")).setText(speech);
		((Text)root.lookup(".int-pane").lookup(".command-text")).setText(speech);

		switch(command[0].toLowerCase())
		{
			case "kitchen":
				if(command[1].equalsIgnoreCase("on")) 	turnTheLightON(0);
				else 											  	turnTheLightOFF(0);
				break;

			case "bedroom":
				if(command[1].equalsIgnoreCase("on")) 	turnTheLightON(1);
				else 											  	turnTheLightOFF(1);
				break;

			case "livingroom":
				if(command[1].equalsIgnoreCase("on")) 	turnTheLightON(2);
				else 											  	turnTheLightOFF(2);
				break;

			case "bathroom":
				if(command[1].equalsIgnoreCase("on")) 	turnTheLightON(3);
				else 											  	turnTheLightOFF(3);
				break;

			case "garage":
				if(command[1].equalsIgnoreCase("on")) 	turnTheLightON(4);
				else 											  	turnTheLightOFF(4);
				break;

			case "hallway":
				if(command[1].equalsIgnoreCase("on")) 	turnTheLightON(5);
				else 											  	turnTheLightOFF(5);
				break;

			case "all":
				if(command[1].equalsIgnoreCase("on"))
				{
					turnTheLightON(0);
					turnTheLightON(1);
					turnTheLightON(2);
					turnTheLightON(3);
					turnTheLightON(4);
					turnTheLightON(5);
				}
				else
				{
					turnTheLightOFF(0);
					turnTheLightOFF(1);
					turnTheLightOFF(2);
					turnTheLightOFF(3);
					turnTheLightOFF(4);
					turnTheLightOFF(5);
				}
				break;

			case "shutdown":
				System.exit(0);
				break;
			default:
				System.out.println("Incorrect command!");
		}


	}

	public boolean getIgnoreSpeechRecognitionResults() {
		return ignoreSpeechRecognitionResults;
	}

	public boolean getSpeechRecognizerThreadRunning() {
		return speechRecognizerThreadRunning;
	}

	/**
	 * Main Method
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		launch(args);
		new Main();
	}
}

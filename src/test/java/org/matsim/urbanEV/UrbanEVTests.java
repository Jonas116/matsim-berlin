package org.matsim.urbanEV;

import com.sun.xml.bind.v2.TODO;
import org.junit.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.contrib.ev.charging.ChargingEndEvent;
import org.matsim.contrib.ev.charging.ChargingEndEventHandler;
import org.matsim.contrib.ev.charging.ChargingStartEvent;
import org.matsim.contrib.ev.charging.ChargingStartEventHandler;
import org.matsim.contrib.ev.fleet.ElectricVehicle;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.run.ev.RunUrbanEVExample;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.Vehicle;

import java.util.*;
import java.util.stream.Collectors;

// TODO translate and complete

/** <b>für (jeden) Agenten:</b>		</b>
		(*) wann wird geladen?	<br>
		(*) wo wird geladen?	<br>
		(*) wird geladen?	<br>
		(*) wie lange wird geladen?	<br>
		(*) wie oft wird geladen?	<br>
		? wird auch bei Fahrzeugwechsel (anderer Mode) geladen?	<br>
		? wird auch 3x geladen?	<br>
		? gleichzeitiges Laden: werden die Fahrzeuge in der richtigen Reihenfolge ein- und ausgestöpselt? (chargingStart und chargingEndEvents) <br>
 		? nicht Lader <br>
 		? zu kurze Ladezeit/falsche Aktivitätentypen <br>
 	<br>
	<b>für jedes Fahrzeug</b>	<br>
		(*) wird am richtigen charger geladen (charger type / leistung)?	<br>

	<b>generell:</b>	<br>
		Konsistenz zw Plugin and Plugout bzgl <br>
		((*) Ort = Link <br>
		(*) Häufigkeit <br>
		(*) .. <br>
**/
public class UrbanEVTests {

	@Rule
	public MatsimTestUtils matsimTestUtils = new MatsimTestUtils();
	private static UrbanEVTestHandler handler;
	private static Map<Id<Person>, List<Activity>> plannedActivitiesPerPerson;

	@BeforeClass
	public static void run(){
		Scenario scenario = CreateUrbanEVTestScenario.createTestScenario();
		scenario.getConfig().controler().setOutputDirectory("test/output/urbanEV/UrbanEVAgentBehaviorTest");

		//modify population
		overridePopulation(scenario);
		plannedActivitiesPerPerson = scenario.getPopulation().getPersons().values().stream()
				.collect(Collectors.toMap(p -> p.getId(),
						p -> TripStructureUtils.getActivities(p.getSelectedPlan(), TripStructureUtils.StageActivityHandling.ExcludeStageActivities)));

		//insert vehicles
		scenario.getVehicles().getVehicles().keySet().forEach(vehicleId -> scenario.getVehicles().removeVehicle(vehicleId));
		CreateUrbanEVTestScenario.createAndRegisterPersonalCarAndBikeVehicles(scenario);

		///controler with Urban EV module
		Controler controler = RunUrbanEVExample.prepareControler(scenario);
		handler = new UrbanEVTestHandler();
		controler.addOverridingModule(new AbstractModule() {
			public void install() {
				this.addEventHandlerBinding().toInstance(handler);
			}
		});
		controler.run();
	}

	@Test
	public void testAgentsExecuteSameNumberOfActs(){

		boolean fail = false;
		String personsWithDifferingActCount = "";
		for (Map.Entry<Id<Person>, List<Activity>> person2Acts : plannedActivitiesPerPerson.entrySet()) {

			List<ActivityStartEvent> executedActs = handler.normalActStarts.get(person2Acts.getKey());
			if(executedActs.size() != person2Acts.getValue().size() - 1 ){ //first act of the day is not started
				fail = true;
				personsWithDifferingActCount += "\n" + person2Acts.getKey() + " plans " + person2Acts.getValue().size() + " activities and executes " + executedActs.size() + " activities";
			}
		}
		Assert.assertFalse("the following persons do not execute the same amount of activities as they plan to:" + personsWithDifferingActCount, fail);
	}

	@Test
	public void testCarAndBikeAgent(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Charge during leisure + bike"));
		Assert.assertEquals( 2, plugins.size(), 0);

		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time",  40491d, pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "90", pluginActStart.getLinkId().toString() );

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Charge during leisure + bike"));
		Assert.assertEquals( 1, plugouts.size(),0);

		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time",  50400d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location",  "90", plugoutActStart.getLinkId().toString());
	}

	@Test
	public void testTripleCharger(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Triple Charger"));
		Assert.assertEquals(plugins.size(), 3, 0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time",  2982d, pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "90", pluginActStart.getLinkId().toString());

		ActivityStartEvent pluginActStart2 = plugins.get(1);
		Assert.assertEquals("wrong charging start time",  8719d, pluginActStart2.getTime(), MatsimTestUtils.EPSILON );
		Assert.assertEquals("wrong charging start location", "90", pluginActStart2.getLinkId().toString());

		ActivityStartEvent pluginActStart3 = plugins.get(2);
		Assert.assertEquals("wrong charging start time",  14456d, pluginActStart3.getTime(), MatsimTestUtils.EPSILON );
		Assert.assertEquals("wrong charging start location", "90", pluginActStart3.getLinkId().toString());

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Triple Charger"));
		Assert.assertEquals(plugouts.size(), 3, 0);
		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time",  4069d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "90", plugoutActStart.getLinkId().toString());

		ActivityEndEvent plugoutActStart2 = plugouts.get(1);
		Assert.assertEquals("wrong charging end time",  9805d, plugoutActStart2.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "90", plugoutActStart2.getLinkId().toString());

		ActivityEndEvent plugoutActStart3 = plugouts.get(2);
		Assert.assertEquals("wrong charging end time",  15542d, plugoutActStart3.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "90", plugoutActStart3.getLinkId().toString());



	}

	@Test
	public void testChargerSelectionShopping(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Charging during shopping"));
		Assert.assertEquals( 1, plugins.size(), 0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time",  36891d, pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "172", pluginActStart.getLinkId().toString());

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Charging during shopping"));
		Assert.assertEquals( 1, plugouts.size(),0);
		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time",  48404d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "172", plugoutActStart.getLinkId().toString());


	}
	@Test
	public void testLongDistance(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Charger Selection long distance leg"));
		Assert.assertEquals( 1, plugins.size(), 0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time",  36459d, pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "89", pluginActStart.getLinkId().toString());

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Charger Selection long distance leg"));
		Assert.assertEquals( 1, plugouts.size(),0);
		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time",  58519d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "89", plugoutActStart.getLinkId().toString());
	}
	@Test
	public void testTwin(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Charger Selection long distance twin"));
		Assert.assertEquals( 1, plugins.size(),0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time", 31300,  pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "89", pluginActStart.getLinkId().toString());

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Charger Selection long distance twin"));
		Assert.assertEquals( 1, plugouts.size(), 0);
		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time",  58159d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "89", plugoutActStart.getLinkId().toString());
	}
	@Test
	public void testDoubleCharger(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Double Charger"));
		Assert.assertEquals( 2, plugins.size(),0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time",  23283d, pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "90", pluginActStart.getLinkId().toString());

		ActivityStartEvent pluginActStart2 = plugins.get(1);
		Assert.assertEquals("wrong charging start time",  39640d, pluginActStart2.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "90", pluginActStart2.getLinkId().toString());

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Double Charger"));
		Assert.assertEquals( 2, plugouts.size(), 0);
		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time",  28783d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "90", plugoutActStart.getLinkId().toString());

		ActivityEndEvent plugoutActStart2 = plugouts.get(1);
		Assert.assertEquals("wrong charging end time",  44668d, plugoutActStart2.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "90", plugoutActStart2.getLinkId().toString());
	}

	@Test
	public void testNotEnoughTimeCharger(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Not enough time Charger"));
		Assert.assertEquals(1, plugins.size(),0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time",  24940d, pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "89", pluginActStart.getLinkId().toString());

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Not enough time Charger"));
		Assert.assertEquals( 1, plugouts.size(),0);
		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time",  47729d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "89", plugoutActStart.getLinkId().toString());
	}

	@Test
	public void testHomeCharger(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Home Charger"));
		Assert.assertEquals(1, plugins.size(),0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time",  6225d, pluginActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", pluginActStart.getLinkId().toString(), "91");

		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Home Charger"));
		Assert.assertEquals("Home charge should not have a plug out interaction",  0, plugouts.size());
	}

	@Test
	public void testDoubleChargerHomeCharger(){
		List<ActivityStartEvent> plugins = this.handler.plugInCntPerPerson.get(Id.createPersonId("Double Charger Home Charger"));
		Assert.assertEquals(plugins.size(),2,0);
		ActivityStartEvent pluginActStart = plugins.get(0);
		Assert.assertEquals("wrong charging start time", pluginActStart.getTime(), 19683d, MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "91", pluginActStart.getLinkId().toString());

		ActivityStartEvent pluginActStart2 = plugins.get(1);
		Assert.assertEquals("wrong charging start time", pluginActStart2.getTime(), 38444d, MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging start location", "90", pluginActStart2.getLinkId().toString());


		List<ActivityEndEvent> plugouts = this.handler.plugOutCntPerPerson.get(Id.createPersonId("Double Charger Home Charger"));
		ActivityEndEvent plugoutActStart = plugouts.get(0);
		Assert.assertEquals("wrong charging end time", 23891d, plugoutActStart.getTime(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("wrong charging end location", "91", plugoutActStart.getLinkId().toString());
		Assert.assertEquals("Should only plug out once ",1, plugouts.size());
	}

	private static void overridePopulation(Scenario scenario) {

		//delete all persons that are there already
		scenario.getPopulation().getPersons().clear();

		PopulationFactory factory = scenario.getPopulation().getFactory();
		Person person = factory.createPerson(Id.createPersonId("Charge during leisure + bike"));


		Plan plan = factory.createPlan();

		Activity home1 = factory.createActivityFromLinkId("home", Id.createLinkId("95"));
		home1.setEndTime(8 * 3600);
		plan.addActivity(home1);

		plan.addLeg(factory.createLeg(TransportMode.car));

		Activity work1 = factory.createActivityFromLinkId("work", Id.createLinkId("24"));
		work1.setEndTime(10 * 3600);
		plan.addActivity(work1);

		plan.addLeg(factory.createLeg(TransportMode.car));

		Activity work12 = factory.createActivityFromLinkId("work", Id.createLinkId("172"));
		work12.setEndTime(11 * 3600);
		plan.addActivity(work12);

		plan.addLeg(factory.createLeg(TransportMode.car));

		Activity leisure1 = factory.createActivityFromLinkId("leisure", Id.createLinkId("90"));
		leisure1.setEndTime(12 * 3600);
		plan.addActivity(leisure1);

		plan.addLeg(factory.createLeg(TransportMode.bike));

		Activity leisure12 = factory.createActivityFromLinkId("leisure", Id.createLinkId("89"));
		leisure12.setEndTime(13 * 3600);
		plan.addActivity(leisure12);

		plan.addLeg(factory.createLeg(TransportMode.bike));

		Activity leisure13 = factory.createActivityFromLinkId("leisure", Id.createLinkId("90"));
		leisure13.setEndTime(14 * 3600);
		plan.addActivity(leisure13);

		plan.addLeg(factory.createLeg(TransportMode.car));


		Activity home12 = factory.createActivityFromLinkId("home", Id.createLinkId("95"));
		home12.setEndTime(15 * 3600);
		plan.addActivity(home12);
		person.addPlan(plan);
		person.setSelectedPlan(plan);


		scenario.getPopulation().addPerson(person);


		Person person2 = factory.createPerson(Id.createPersonId("Charging during shopping"));

		Plan plan2 = factory.createPlan();

		Activity home21 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home21.setEndTime(8 * 3600);
		plan2.addActivity(home21);
		plan2.addLeg(factory.createLeg(TransportMode.car));

		Activity work21 = factory.createActivityFromLinkId("work", Id.createLinkId("175"));
		work21.setEndTime(10 * 3600);
		plan2.addActivity(work21);

		plan2.addLeg(factory.createLeg(TransportMode.car));

		Activity work22 = factory.createActivityFromLinkId("work", Id.createLinkId("60"));
		work22.setEndTime(12 * 3600);
		plan2.addActivity(work22);

		plan2.addLeg(factory.createLeg(TransportMode.car));

		Activity shopping21 = factory.createActivityFromLinkId("shopping", Id.createLinkId("9"));
		shopping21.setMaximumDuration(1200);

		plan2.addActivity(shopping21);

		plan2.addLeg(factory.createLeg(TransportMode.car));

		Activity work23 = factory.createActivityFromLinkId("work", Id.createLinkId("5"));
		work23.setEndTime(13 * 3600);
		plan2.addActivity(work23);

		plan2.addLeg(factory.createLeg(TransportMode.car));

		Activity home22 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home22.setEndTime(15 * 3600);
		plan2.addActivity(home22);
		person2.addPlan(plan2);
		person2.setSelectedPlan(plan2);

		scenario.getPopulation().addPerson(person2);

		Person person3 = factory.createPerson(Id.createPersonId("Charger Selection long distance leg"));

		Plan plan3 = factory.createPlan();

		Activity home31 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home31.setEndTime(8 * 3600);
		plan3.addActivity(home31);
		plan3.addLeg(factory.createLeg(TransportMode.car));

		Activity work31 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work31.setEndTime(10 * 3600);
		plan3.addActivity(work31);

		plan3.addLeg(factory.createLeg(TransportMode.car));

		Activity work32 = factory.createActivityFromLinkId("work", Id.createLinkId("99"));
		work32.setEndTime(12 * 3600);
		plan3.addActivity(work32);

		plan3.addLeg(factory.createLeg(TransportMode.car));

		Activity home32 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home32.setEndTime(15 * 3600);
		plan3.addActivity(home32);
		person3.addPlan(plan3);
		person3.setSelectedPlan(plan3);

		scenario.getPopulation().addPerson(person3);

		Person person4 = factory.createPerson(Id.createPersonId("Charger Selection long distance twin"));

		Plan plan4 = factory.createPlan();

		Activity home41 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home41.setEndTime(8 * 3605);
		plan4.addActivity(home41);
		plan4.addLeg(factory.createLeg(TransportMode.car));

		Activity work41 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work41.setEndTime(10 * 3600);
		plan4.addActivity(work41);

		plan4.addLeg(factory.createLeg(TransportMode.car));

		Activity work42 = factory.createActivityFromLinkId("work", Id.createLinkId("99"));
		work42.setEndTime(12 * 3600);
		plan4.addActivity(work42);

		plan4.addLeg(factory.createLeg(TransportMode.car));

		Activity home42 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home42.setEndTime(15 * 3600);
		plan4.addActivity(home42);
		person4.addPlan(plan4);
		person4.setSelectedPlan(plan4);

		scenario.getPopulation().addPerson(person4);

		Person person5 = factory.createPerson(Id.createPersonId("Triple Charger"));

		Plan plan5 = factory.createPlan();

		Activity home51 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home51.setMaximumDuration(1*1200);
		plan5.addActivity(home51);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work51 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work51.setMaximumDuration(1*1200);
		plan5.addActivity(work51);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work52 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		work52.setMaximumDuration(1*1200);
		plan5.addActivity(work52);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work53 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work53.setMaximumDuration(1*1200);
		plan5.addActivity(work53);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity home52 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		home52.setMaximumDuration(1*1200);
		plan5.addActivity(home52);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work54 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work54.setMaximumDuration(1*1200);
		plan5.addActivity(work54);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work55 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		work55.setMaximumDuration(1*1200);
		plan5.addActivity(work55);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work56 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work56.setMaximumDuration(1*1200);
		plan5.addActivity(work56);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity home53 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		home53.setMaximumDuration(1*1200);
		plan5.addActivity(home53);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work57 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work57.setMaximumDuration(1*1200);
		plan5.addActivity(work57);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work58 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		work58.setMaximumDuration(1*1200);
		plan5.addActivity(work58);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work59 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work59.setMaximumDuration(1*1200);
		plan5.addActivity(work59);
		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity home54 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		home54.setMaximumDuration(1*1200);
		plan5.addActivity(home54);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work510 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work510.setMaximumDuration(1*1200);
		plan5.addActivity(work510);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work511 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		work511.setMaximumDuration(1*1200);
		plan5.addActivity(work511);

		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity work512 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work512.setMaximumDuration(1*1200);
		plan5.addActivity(work512);
		plan5.addLeg(factory.createLeg(TransportMode.car));

		Activity home55 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home55.setMaximumDuration(1*1200);
		plan5.addActivity(home55);


		person5.addPlan(plan5);
		person5.setSelectedPlan(plan5);

		scenario.getPopulation().addPerson(person5);

		Person person6= factory.createPerson(Id.createPersonId("Double Charger"));

		Plan plan6 = factory.createPlan();

		Activity home61 = factory.createActivityFromLinkId("home", Id.createLinkId("2"));
		home61.setEndTime(6*3600);
		plan6.addActivity(home61);
		plan6.addLeg(factory.createLeg(TransportMode.car));

		Activity work61 = factory.createActivityFromLinkId("work", Id.createLinkId("179"));
		work61.setMaximumDuration(1200);
		plan6.addActivity(work61);

		plan6.addLeg(factory.createLeg(TransportMode.car));

		Activity work62 = factory.createActivityFromLinkId("work", Id.createLinkId("2"));
		work62.setMaximumDuration(1200);
		plan6.addActivity(work62);

		plan6.addLeg(factory.createLeg(TransportMode.car));

		Activity work63 = factory.createActivityFromLinkId("work", Id.createLinkId("179"));
		work63.setMaximumDuration(1200);
		plan6.addActivity(work63);

		plan6.addLeg(factory.createLeg(TransportMode.car));

		Activity work64 = factory.createActivityFromLinkId("work", Id.createLinkId("2"));
		work64.setMaximumDuration(1200);
		plan6.addActivity(work64);

		plan6.addLeg(factory.createLeg(TransportMode.car));

		Activity work65 = factory.createActivityFromLinkId("work", Id.createLinkId("179"));
		work65.setMaximumDuration(1200);
		plan6.addActivity(work65);

		plan6.addLeg(factory.createLeg(TransportMode.car));


		Activity home62 = factory.createActivityFromLinkId("home", Id.createLinkId("2"));
		home62.setMaximumDuration(1200);
		plan6.addActivity(home62);




		person6.addPlan(plan6);
		person6.setSelectedPlan(plan6);
		scenario.getPopulation().addPerson(person6);

		Person person7= factory.createPerson(Id.createPersonId("Not enough time Charger"));

		Plan plan7 = factory.createPlan();

		Activity home71 = factory.createActivityFromLinkId("home", Id.createLinkId("1"));
		home71.setEndTime(6*3600);
		plan7.addActivity(home71);
		plan7.addLeg(factory.createLeg(TransportMode.car));

		Activity work71 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work71.setMaximumDuration(1200);
		plan7.addActivity(work61);

		plan7.addLeg(factory.createLeg(TransportMode.car));

		Activity work72 = factory.createActivityFromLinkId("work", Id.createLinkId("99"));
		work72.setMaximumDuration(1200);
		plan7.addActivity(work72);

		plan7.addLeg(factory.createLeg(TransportMode.car));

		Activity work73 = factory.createActivityFromLinkId("work", Id.createLinkId("92"));
		work73.setMaximumDuration(1140);
		plan7.addActivity(work73);

		plan7.addLeg(factory.createLeg(TransportMode.car));

		Activity work74 = factory.createActivityFromLinkId("work", Id.createLinkId("75"));
		work74.setMaximumDuration(1200);
		plan7.addActivity(work74);

		plan7.addLeg(factory.createLeg(TransportMode.car));

		Activity work75 = factory.createActivityFromLinkId("work", Id.createLinkId("179"));
		work75.setMaximumDuration(1200);
		plan7.addActivity(work75);

		plan7.addLeg(factory.createLeg(TransportMode.car));


		Activity home72 = factory.createActivityFromLinkId("home", Id.createLinkId("2"));
		home72.setMaximumDuration(1200);
		plan7.addActivity(home72);

		person7.addPlan(plan7);
		person7.setSelectedPlan(plan7);
		scenario.getPopulation().addPerson(person7);


		Person person8 = factory.createPerson(Id.createPersonId("Home Charger"));
		Plan plan8 = factory.createPlan();

		Activity home8 = factory.createActivityFromLinkId("home", Id.createLinkId("91"));
		home8.setMaximumDuration(1200);
		plan8.addActivity(home8);

		plan8.addLeg(factory.createLeg(TransportMode.car));

		Activity work8 = factory.createActivityFromLinkId("work", Id.createLinkId("180"));
		work8.setMaximumDuration(1200);
		plan8.addActivity(work8);

		plan8.addLeg(factory.createLeg(TransportMode.car));

		Activity home81 = factory.createActivityFromLinkId("home", Id.createLinkId("91"));
		home81.setMaximumDuration(1200);
		plan8.addActivity(home81);

		plan8.addLeg(factory.createLeg(TransportMode.bike));

		Activity leisure81 = factory.createActivityFromLinkId("leisure", Id.createLinkId("5"));
		leisure81.setMaximumDuration(1200);
		plan8.addActivity(leisure81);
		plan8.addLeg(factory.createLeg(TransportMode.bike));

		Activity leisure82 = factory.createActivityFromLinkId("leisure", Id.createLinkId("91"));
		leisure82.setMaximumDuration(1200);
		plan8.addActivity(leisure82);


		person8.addPlan(plan8);
		person8.setSelectedPlan(plan8);
		scenario.getPopulation().addPerson(person8);

		Person person9 = factory.createPerson(Id.createPersonId("Double Charger Home Charger"));
		Plan plan9 = factory.createPlan();

		Activity home91 = factory.createActivityFromLinkId("home", Id.createLinkId("90"));
		home91.setEndTime(5*3600);
		plan9.addActivity(home91);

		plan9.addLeg(factory.createLeg(TransportMode.car));

		Activity work91 = factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		work91.setMaximumDuration(1*1200);
		plan9.addActivity(work91);

		plan9.addLeg(factory.createLeg(TransportMode.car));

		Activity work92 = factory.createActivityFromLinkId("work", Id.createLinkId("90"));
		work92.setMaximumDuration(1*1200);
		plan9.addActivity(work92);

		plan9.addLeg(factory.createLeg(TransportMode.car));

		Activity work93 = factory.createActivityFromLinkId("work", Id.createLinkId("80"));
		work93.setMaximumDuration(1*1200);
		plan9.addActivity(work93);

		plan9.addLeg(factory.createLeg(TransportMode.car));

		Activity work94= factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		work94.setMaximumDuration(1*1200);
		plan9.addActivity(work94);

		plan9.addLeg(factory.createLeg(TransportMode.car));

		Activity work95 = factory.createActivityFromLinkId("work", Id.createLinkId("80"));
		work95.setMaximumDuration(1*1200);
		plan9.addActivity(work95);

		plan9.addLeg(factory.createLeg(TransportMode.car));

		Activity work96= factory.createActivityFromLinkId("work", Id.createLinkId("1"));
		work96.setMaximumDuration(1*1200);
		plan9.addActivity(work96);

		plan9.addLeg(factory.createLeg(TransportMode.car));

		Activity home92 = factory.createActivityFromLinkId("home", Id.createLinkId("90"));
		home92.setEndTime(1200);
		plan9.addActivity(home92);


		plan9.addLeg(factory.createLeg(TransportMode.bike));

		Activity leisure91 = factory.createActivityFromLinkId("leisure", Id.createLinkId("5"));
		leisure91.setMaximumDuration(1200);
		plan9.addActivity(leisure91);

		plan9.addLeg(factory.createLeg(TransportMode.bike));

		Activity leisure92 = factory.createActivityFromLinkId("leisure", Id.createLinkId("91"));
		leisure92.setMaximumDuration(1200);
		plan9.addActivity(leisure92);

		person9.addPlan(plan9);
		person9.setSelectedPlan(plan9);
		scenario.getPopulation().addPerson(person9);
	}

//	-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
	/**
	 *
	 * tests (every iteration) <br>
	 * * location consistency between plugin and plugout <br>
	 * * nrOfPlugins == nrOfPlugouts for each person <br>
	 * * nr of all charging persons <br>
	 * * consistency of chargers between charging start and charging end <br>
	 *
	 */
	private static class UrbanEVTestHandler implements ActivityStartEventHandler, ActivityEndEventHandler , ChargingStartEventHandler, ChargingEndEventHandler {

		private Map<Id<Person>, List<ActivityEndEvent>> plugOutCntPerPerson = new HashMap<>();
		private Map<Id<Person>, List<ActivityStartEvent>> plugInCntPerPerson = new HashMap<>();
		private Map<Id<Person>, List<ActivityStartEvent>> normalActStarts = new HashMap<>();
		private Map<Id<ElectricVehicle>, List<ChargingStartEvent>> chargingStarts = new HashMap<>();
		private Map<Id<ElectricVehicle>, List<ChargingEndEvent>> chargingEnds = new HashMap<>();


		@Override
		public void handleEvent(ActivityStartEvent event) {
			if( event.getActType().contains(UrbanVehicleChargingHandler.PLUGIN_INTERACTION) ){
				compute(plugInCntPerPerson, event);
			} else if (! TripStructureUtils.isStageActivityType(event.getActType())){
				compute(normalActStarts, event);
			}
		}

		@Override
		public void handleEvent(ActivityEndEvent event) {
			if( event.getActType().contains(UrbanVehicleChargingHandler.PLUGOUT_INTERACTION) ){
				plugOutCntPerPerson.compute(event.getPersonId(), (person,list) ->{
					if (list == null) list = new ArrayList<>();
					list.add(event);
					return list;
				});

				ActivityStartEvent correspondingPlugin = this.plugInCntPerPerson.get(event.getPersonId()).get(this.plugInCntPerPerson.get(event.getPersonId()).size() - 1);
				Assert.assertEquals("plugin and plugout location seem not to match. event=" + event, correspondingPlugin.getLinkId(), event.getLinkId());
			}
		}

		@Override
		public void reset(int iteration) {
			if(iteration > 0){
				System.out.println("ITERATION = " + iteration);

				//TODO move the following assert statements out of the simulation loop? Or do we want to explicitly check these _every_ iteration?

				Assert.assertEquals("there should be 9 people plugging in in this test", 9, plugInCntPerPerson.size(), 0);
				Assert.assertEquals("there should be 8 people plugging out this test", 9, plugOutCntPerPerson.size(), 0);
//				Assert.assertEquals( plugInCntPerPerson.size(), plugOutCntPerPerson.size()); //not necessary

//				Assert.assertTrue(plugInCntPerPerson.containsKey(Id.createPersonId("Charger Selection long distance leg")));

				//	The number of plug in and outs is not equal anymore since we added homecharging

//				for (Id<Person> personId : plugInCntPerPerson.keySet()) {
//					Assert.assertTrue("in this test, each agent should only plugin once. agent=" + personId,
//							plugInCntPerPerson.get(personId).size() >= 1);
//					Assert.assertTrue( "each agent should plug in just as often as it plugs out. agent = " + personId,
//							plugInCntPerPerson.get(personId).size() == plugOutCntPerPerson.get(personId).size());
//				}
			}

			this.plugInCntPerPerson.clear();
			this.plugOutCntPerPerson.clear();
			this.normalActStarts.clear();
		}

		@Override
		public void handleEvent(ChargingEndEvent event) {
			this.chargingEnds.compute(event.getVehicleId(), (person,list) ->{
				if (list == null) list = new ArrayList<>();
				list.add(event);
				return list;
			});


			ChargingStartEvent correspondingStart = this.chargingStarts.get(event.getVehicleId()).get(this.chargingStarts.get(event.getVehicleId()).size() - 1);
			Assert.assertEquals("chargingEnd and chargingStart do not seem not to take place at the same charger. event=" + event, correspondingStart.getChargerId(), event.getChargerId());
		}

		@Override
		public void handleEvent(ChargingStartEvent event) {
			this.chargingStarts.compute(event.getVehicleId(), (person,list) ->{
				if (list == null) list = new ArrayList<>();
				list.add(event);
				return list;
			});
		}
	}

	private static void compute(Map<Id<Person>, List<ActivityStartEvent>> map, ActivityStartEvent event) {
		map.compute(event.getPersonId(), (person,list) ->{
			if (list == null) list = new ArrayList<>();
			list.add(event);
			return list;
		});
	}

}
package com.patson.model

import com.patson.PassengerSimulation.LINK_COST_TOLERANCE_FACTOR
import com.patson.model.airplane._
import com.patson.data.{AirlineSource, AirplaneSource, AirportAssetSource, AirportSource, AllianceSource, BankSource, CountrySource, CycleSource, OilSource}
import com.patson.Util
import com.patson.util.{AirlineCache, AllianceRankingUtil}

import scala.collection.mutable.ListBuffer

object Computation {
  val MODEL_COUNTRY_CODE = "US"
  val MODEL_COUNTRY_POWER : Double = CountrySource.loadCountryByCode(MODEL_COUNTRY_CODE) match {
    case Some(country) =>
      country.airportPopulation.toDouble * country.income
    case None =>
      println(s"Cannot find $MODEL_COUNTRY_CODE to compute model power")
      1
  }

  lazy val MAX_VALUES = getMaxValues()
  lazy val MODEL_AIRPORT_POWER = MAX_VALUES._1
  lazy val MAX_POPULATION = MAX_VALUES._3
  lazy val MAX_INCOME = MAX_VALUES._4

  val MAX_COMPUTED_DISTANCE = 20000
  lazy val standardFlightDurationCache : Array[Int] = {
    val result = new Array[Int](MAX_COMPUTED_DISTANCE + 1)
    for (i <- 0 to MAX_COMPUTED_DISTANCE) { //should cover everything...
      result(i) =  Computation.internalComputeStandardFlightDuration(i)
    }
    result
  }

  def getMaxValues(): (Long, Double, Long, Long) = {
    val allAirports = AirportSource.loadAllAirports()
    //take note that below should NOT use boosted values, should use base, otherwise it will incorrectly load some lazy vals of the Airport that is MAX
    (allAirports.maxBy(_.basePower).basePower, allAirports.maxBy(_.baseIncomeLevel).baseIncomeLevel, allAirports.maxBy(_.basePopulation).basePopulation, allAirports.maxBy(_.baseIncome).baseIncome)
  }

  def calculateDuration(airplaneModel: Model, distance : Int) : Int = {
    val speed =
      if (airplaneModel.category == com.patson.model.airplane.Model.Category.SUPERSONIC) {
        (airplaneModel.speed * 1.5).toInt //up adjusted for SST
      } else {
        airplaneModel.speed
      }
    val speedLimits = if (airplaneModel.category == com.patson.model.airplane.Model.Category.PROPELLER) {
      List((250, 400), (350, 525))
    } else {
      List((300, 350), (400, 500), (400, 700))
    }
    calculateDuration(speed, distance, speedLimits)
  }
  def calculateDuration(airplaneSpeed : Int, distance : Int, speedLimits : List[(Int, Int)] = List((300, 350), (400, 500), (400, 700))) = {
    var remainDistance = distance
    var duration = 0;
    for ((distanceBucket, maxSpeed) <- speedLimits if (remainDistance > 0)) {
      val speed = Math.min(maxSpeed, airplaneSpeed)
      if (distanceBucket >= remainDistance) {
        duration += remainDistance * 60 / speed
      } else {
        duration += distanceBucket * 60 / speed
      }
      remainDistance -= distanceBucket
    }

    if (remainDistance > 0) {
      duration += remainDistance * 60 / airplaneSpeed
    }
    duration
  }


  def calculateFlightMinutesRequired(airplaneModel : Model, distance : Int) : Int = {
    val duration = calculateDuration(airplaneModel, distance)
    val roundTripTime = (duration + airplaneModel.turnaroundTime) * 2
    roundTripTime
  }

  def calculateMaxFrequency(airplaneModel : Model, distance : Int) : Int = {
    if (airplaneModel.range < distance) {
      0
    } else {
      val roundTripTime = calculateFlightMinutesRequired(airplaneModel, distance)
      (Airplane.MAX_FLIGHT_MINUTES / roundTripTime).toInt
    }
  }
  

  val SELL_RATE = 0.8
  
  def calculateAirplaneSellValue(airplane : Airplane) : Int = {
    val value = airplane.value * airplane.purchaseRate * SELL_RATE //airplane.purchase < 1 means it was bought with a discount, selling should be lower price
    if (value < 0) 0 else value.toInt
  }
  
  def calculateDistance(fromAirport : Airport, toAirport : Airport) : Int = {
    Util.calculateDistance(fromAirport.latitude, fromAirport.longitude, toAirport.latitude, toAirport.longitude).toInt
  }

  def getFlightType(fromAirport : Airport, toAirport : Airport) : FlightType.Value = {
    val relationship = CountrySource.getCountryMutualRelationship(fromAirport.countryCode, toAirport.countryCode)
    getFlightType(fromAirport, toAirport, calculateDistance(fromAirport, toAirport), relationship)
  }

  //not passing relationship in getRouteRejection()
  def getFlightType(fromAirport : Airport, toAirport : Airport, distance : Int, relationship : Int = 0) = {
    import FlightType._
    //hard-coding some home markets into the computation function to allow for independent relation values
    //https://en.wikipedia.org/wiki/European_Common_Aviation_Area
    val ECAA = List("AL", "AM", "AT", "BA", "BE", "BG", "CH", "CY", "CZ", "DK", "EE", "FI", "FR", "DE", "GE", "GR", "HR", "HU", "IE", "IS", "IT", "LT", "LU", "LV", "MD", "ME", "MK", "MT", "NL", "NO", "PL", "PT", "RO", "RS", "SI", "SK", "ES", "SE", "UA", "XK")
    val ANZAC = List("AU", "NZ", "CX", "CK", "NU", "CC")
    val USA = List("US", "PR", "VI", "GU", "AS", "MP", "MH", "PW", "FM") //US & COFA Pacific
    if (fromAirport.countryCode == toAirport.countryCode || relationship == 5 || ECAA.contains(fromAirport.countryCode) && ECAA.contains(toAirport.countryCode) || ANZAC.contains(fromAirport.countryCode) && ANZAC.contains(toAirport.countryCode) || USA.contains(fromAirport.countryCode) && USA.contains(toAirport.countryCode)){
      if (distance <= 1000) {
        SHORT_HAUL_DOMESTIC
      } else if (distance <= 3000) {
        MEDIUM_HAUL_DOMESTIC
      } else if (distance <= 9000) {
        LONG_HAUL_DOMESTIC
      } else {
        ULTRA_LONG_HAUL_DOMESTIC
      }
    } else {
      if (distance <= 1000) {
        SHORT_HAUL_INTERNATIONAL
      } else if (distance <= 3000) {
        MEDIUM_HAUL_INTERNATIONAL
      } else if (distance <= 9000) {
        LONG_HAUL_INTERNATIONAL
      } else {
        ULTRA_LONG_HAUL_INTERCONTINENTAL
      }
    }
  }

  /**
   * Matches shared strings, deliminated by a dash "-"
   * international only, any that start with a "|"
   * diaspora, discards strings with |first-code|second-code| pattern unless toCountryCode & fromCountryCode are also matched (only different for text, not value)
   *
   * @param fromZone
   * @param toZone
   * @param toCountryCode
   * @param relationship
   * @return
   */

def calculateAffinityValue(fromZone : String, toZone : String, relationship : Int) : Int = {
  val relationshipModifier =
    if (relationship >= 5) { //domestic
      5
    } else if (relationship < 0) {
      Math.floor(relationship.toDouble / 2.0).toInt
    } else if (relationship > 0) {
      (relationship / 2).toInt
    } else {
      0
    }

  val set1 = if (relationship >= 5) {
    fromZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    fromZone.split("-").filter(_!="None|")
  }
  val set2 = if (relationship >= 5) {
    toZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    toZone.split("-").filter(_!="None|")
  }
  val affinitySet = set1.intersect(set2)
  countX2(affinitySet) + affinitySet.size + relationshipModifier
}

def countX2(strings: Array[String]): Int = {
  strings.count { str =>
    str.length >= 5 && str.substring(str.length - 3, str.length - 1) == "x2"
  }
}

def constructAffinityText(fromZone : String, toZone : String, fromCountry : String, toCountry : String, relationship : Int, affinity : Int) : String = {
  val set1 = if (relationship >= 5) {
    fromZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    fromZone.split("-").filter(_ != "None|")
  }
  val set2 = if (relationship >= 5) {
    toZone.split("-").filterNot(_.endsWith("|")).filterNot(_.startsWith("|"))
  } else {
    toZone.split("-").filter(_ != "None|")
  }
  var matchingItems = set1.intersect(set2).toArray

  if (relationship <= 5) {
    matchingItems = matchingItems.map(item => {
      if (item.endsWith("|")) item.dropRight(1)
      else if (item.startsWith("|")) item.drop(4) + " diaspora"
      else item
    })
  }

  if (relationship <= -1) {
    matchingItems = Array("Political Acrimony") ++ matchingItems
  } else if (relationship >= 5) {
    matchingItems = Array("Domestic") ++ matchingItems
  } else if (relationship >= 4) {
    matchingItems = Array("Excellent Relations") ++ matchingItems
  } else if (relationship >= 2) {
    matchingItems = Array("Good Relations") ++ matchingItems
  }

  val introText = if (affinity == 0 && matchingItems.size == 0){
    "Neutral"
  } else if (affinity == 0){
    "Neutral:"
  } else if (affinity > 0){
    s"+${affinity}:"
  } else {
    s"${affinity}:"
  }

  s"${introText} ${matchingItems.mkString(", ")}"
}
  

  /**
   * Returns income level, should be greater than 0
   */
  def getIncomeLevel(income : Int) : Double = {
    val incomeLevel = income.toDouble / 1000
    if (incomeLevel < 1) {
      1
    } else {
      incomeLevel
    }
  }
  def fromIncomeLevel(incomeLevel : Double) : Int = {
    (incomeLevel * 1000).toInt
  }

  def computeIncomeLevelBoostFromPercentage(baseIncome : Int, minIncomeBoost : Int, boostPercentage : Int) = {
    val incomeIncrement = baseIncome * boostPercentage / 100
    val incomeBoost = Math.max(minIncomeBoost, incomeIncrement)

    //10% would always be 1, but cannot make assumption of our income level calculation tho...
    val baseIncomeLevel = getIncomeLevel(baseIncome)
    BigDecimal(Computation.getIncomeLevel(baseIncome + incomeBoost) - baseIncomeLevel).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  /**
    * For low income base, use the boost level (which is MAX boost). For higher income base, down adjust it to certain
    * percentage
    * @param baseIncome
    * @param boostLevel
    * @return
    */
  def computeIncomeLevelBoostFromLevel(baseIncome : Int, boostLevel : Double) = {
    val newIncomeLevel = getIncomeLevel(baseIncome) + boostLevel
    val incomeIncrement = fromIncomeLevel(newIncomeLevel) - baseIncome
    val maxIncomeBoost = (boostLevel * 10_000).toInt //a bit arbitrary
    val minIncomeBoost = (boostLevel * 2_500).toInt
    val finalBoostLevel =
      if (incomeIncrement < minIncomeBoost) {
        getIncomeLevel(baseIncome + minIncomeBoost) - getIncomeLevel(baseIncome)
      } else if (incomeIncrement <= maxIncomeBoost) {
        boostLevel
      } else {
        getIncomeLevel(baseIncome + maxIncomeBoost) - getIncomeLevel(baseIncome)
      }

    BigDecimal(finalBoostLevel).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  import org.apache.commons.math3.distribution.{LogNormalDistribution, NormalDistribution}

  def populationAboveThreshold(meanIncome: Double, population: Int, gini: Double, threshold: Int): Int = {
//    val normalDistribution = new NormalDistribution(meanIncome, meanIncome * gini / 100)

    //larger urban areas have much more inequality
    val urbanInequalityModifier = if (population > 16000000) {
      1.6
    } else if (population > 8000000){
      1.5
    } else if (population > 4000000) {
      1.4
    } else if (population > 2000000){
      1.3
    } else if (population > 1000000){
      1.1
    } else if (population > 100000){
      0.9
    } else {
      0.7
    }

    val meanLog = Math.log(meanIncome)
    val sdLog = gini / 100 * urbanInequalityModifier
    val logDistribution = new LogNormalDistribution(meanLog, sdLog)

    val probLognormal = 1.0 - logDistribution.cumulativeProbability(threshold)


    // Could do a normal dist + log
//    val normalPop = math.min(population, 1000000) // First 1 million
//    val lognormalPop = math.max(population - 1000000, 0)
//    val probNormal = 1.0 - normalDistribution.cumulativeProbability(threshold)

    // (normalPop * probNormal + lognormalPop * probLognormal).round.toInt
    (population * probLognormal).round.toInt
  }

  def getLinkCreationCost(from : Airport, to : Airport) : Int = {
    
    val baseCost = 100000 + (from.income + to.income)
      
    val minAirportSize = Math.min(from.size, to.size) //encourage links for smaller airport
    
    val airportSizeMultiplier = Math.pow(1.5, minAirportSize) 
    val distance = calculateDistance(from, to)
    val distanceMultiplier = distance.toDouble / 5000
    val internationalMultiplier = if (from.countryCode == to.countryCode) 1 else 3
    
    (baseCost * airportSizeMultiplier * distanceMultiplier * internationalMultiplier).toInt 
  }

  val REDUCED_COMPENSATION_SERVICE_LEVEL_THRESHOLD = 40 //airline with service level below this will pay less compensation
  
  def computeCompensation(link : Link) : Int = {
    if (link.majorDelayCount > 0 || link.minorDelayCount > 0 || link.cancellationCount > 0 ) {
      val soldSeatsPerFlight = link.soldSeats / link.frequency
      val halfCapacityPerFlight = link.capacity / link.frequency * 0.5
      
      val affectedSeatsPerFlight = if (soldSeatsPerFlight.total > halfCapacityPerFlight.total) soldSeatsPerFlight else halfCapacityPerFlight //if less than 50% LF, considered that as 50% LF
      var compensation = (affectedSeatsPerFlight * link.cancellationCount * 0.5 * link.price).total  //50% of ticket price, as there's some penalty for that already
      compensation = compensation + (affectedSeatsPerFlight * link.majorDelayCount * 0.3 * link.price).total //30% of ticket price
      compensation = compensation + (affectedSeatsPerFlight * link.minorDelayCount * 0.05 * link.price).total //5% of ticket price

      if (link.airline.getCurrentServiceQuality() < REDUCED_COMPENSATION_SERVICE_LEVEL_THRESHOLD) { //down to only 20%
        val ratio = 0.2 + 0.8 * link.airline.getCurrentServiceQuality() / REDUCED_COMPENSATION_SERVICE_LEVEL_THRESHOLD
        (compensation * ratio).toInt
      } else {
        compensation.toInt
      }
    } else {
      0
    }
  }
  
  def getResetAmount(airlineId : Int) : ResetAmountInfo = {
    val currentCycle = CycleSource.loadCycle()
    val amountFromAirplanes = AirplaneSource.loadAirplanesByOwner(airlineId, false).map(Computation.calculateAirplaneSellValue(_).toLong).sum
    val amountFromBases = AirlineSource.loadAirlineBasesByAirline(airlineId).map(_.getValue * 0.2).sum.toLong //only get 20% back
    val amountFromAssets = AirportAssetSource.loadAirportAssetsByAirline(airlineId).map(_.sellValue).sum
    val amountFromLoans = BankSource.loadLoansByAirline(airlineId).map(_.earlyRepayment(currentCycle) * -1).sum //repay all loans now
    val amountFromOilContracts = OilSource.loadOilContractsByAirline(airlineId).map(_.contractTerminationPenalty(currentCycle) * -1).sum //termination penalty
    val existingBalance = AirlineCache.getAirline(airlineId).get.airlineInfo.balance
    
    ResetAmountInfo(amountFromAirplanes, amountFromBases, amountFromAssets, amountFromLoans, amountFromOilContracts, existingBalance)
  }
  
  case class ResetAmountInfo(airplanes : Long, bases : Long, assets : Long, loans : Long, oilContracts : Long, existingBalance : Long) {
    val overall = airplanes + bases + assets + loans + oilContracts + existingBalance
  }

  val MAX_SATISFACTION_PRICE_RATIO_THRESHOLD = 0.7 //at 100% satisfaction is <= this threshold
  val MIN_SATISFACTION_PRICE_RATIO_THRESHOLD = LINK_COST_TOLERANCE_FACTOR + 0.05 //0% satisfaction >= this threshold ... +0.05 so, there will be at least some satisfaction even at the LINK_COST_TOLERANCE_FACTOR
  /**
    * From 0 (not satisfied at all) to 1 (fully satisfied)
    *
    *
    */
  val computePassengerSatisfaction = (cost: Double, standardPrice : Int) => {
    val ratio = cost / standardPrice
    var satisfaction = (MIN_SATISFACTION_PRICE_RATIO_THRESHOLD - ratio) / (MIN_SATISFACTION_PRICE_RATIO_THRESHOLD - MAX_SATISFACTION_PRICE_RATIO_THRESHOLD)
    satisfaction = Math.min(1, Math.max(0, satisfaction))
    //println(s"${cost} vs standard price $standardPrice. satisfaction : ${satisfaction}")
    satisfaction
  }

  val computeStandardFlightDuration = (distance: Int) => {
    if (distance <= MAX_COMPUTED_DISTANCE) {
      standardFlightDurationCache(distance)
    } else {
      println(s"Unexpected distance $distance")
      internalComputeStandardFlightDuration(distance) //just in case
    }
  }
  private def internalComputeStandardFlightDuration(distance : Int) = {
    val standardSpeed =
      if (distance <= 1000) {
        400
      } else if (distance <= 2000) {
        600
      } else {
        800
      }
    Computation.calculateDuration(standardSpeed, distance)
  }

  def getDomesticAirportWithinRange(principalAirport : Airport, range : Int) = { //range in km
    val affectedAirports = ListBuffer[Airport]()
    AirportSource.loadAirportsByCountry(principalAirport.countryCode).foreach { airport =>
      if (Computation.calculateDistance(principalAirport, airport) <= range) {
        affectedAirports.append(airport)
      }
    }
    affectedAirports.toList
  }
}
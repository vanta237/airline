package com.patson.model.airplane

import com.patson.model.IdObject
import com.patson.model.Airline
import com.patson.model.airplane.Model.Category
import com.patson.util.AirplaneModelCache

case class Model(name : String, family : String = "", capacity : Int, maxSeats : Int, quality : Double, fuelBurn : Int, speed : Int, range : Int, price : Int, lifespan : Int, constructionTime : Int, manufacturer: Manufacturer, runwayRequirement : Int, imageUrl : String = "", var id : Int = 0) extends IdObject {
  import Model.Type._

  val countryCode = manufacturer.countryCode
  val SUPERSONIC_SPEED_THRESHOLD = 1236
  val SIZE_SMALL_THRESHOLD = 80

  val airplaneType : Type = {
    if (speed > SUPERSONIC_SPEED_THRESHOLD) {
      SUPERSONIC
    } else if (speed < 180) {
      AIRSHIP
    } else if (speed <= 300) {
      HELICOPTER
    } else if (speed <= 680) {
      if (capacity <= SIZE_SMALL_THRESHOLD) {
        PROPELLER_SMALL
      } else {
        PROPELLER_MEDIUM
      }
    } else if (capacity <= SIZE_SMALL_THRESHOLD) {
      SMALL
    } else if (capacity <= 131 || family == "Embraer E-Jet E2") {
      REGIONAL
    } else if (capacity <= 215) {
      MEDIUM
    } else if (family == "Boeing 757" || family == "Boeing 737"  || family == "Airbus A320" || family == "DC-8" || family == "Yakovlev MC-21") {
      MEDIUM_XL
    } else if (capacity <= 375) {
      LARGE
    } else if (capacity <= 475) {
      EXTRA_LARGE
    } else {
      JUMBO
    }
  }

  val category: Model.Category.Value = Category.fromType(airplaneType)

  private[this]val BASE_TURNAROUND_TIME = 30
  val turnaroundTime : Int = {
    BASE_TURNAROUND_TIME +
      (airplaneType match {
        case HELICOPTER => 0 //buffing for short distances
        case SMALL =>  maxSeats / 3 //70
        case REGIONAL => maxSeats / 3
        case MEDIUM => maxSeats / 2.5
        case MEDIUM_XL => maxSeats / 2.5
        case LARGE => maxSeats / 2.5
        case EXTRA_LARGE => maxSeats / 2.5
        case JUMBO => maxSeats / 2.5
        case _ => maxSeats / 3
      }).toInt
  }

  val airplaneTypeLabel : String = label(airplaneType)

  val airplaneTypeSize : Double = size(airplaneType)

  //weekly fixed cost
  val baseMaintenanceCost : Int = {
    (capacity * 120).toInt //for now
  }

  def applyDiscount(discounts : List[ModelDiscount]) = {
    var discountedModel = this
    discounts.groupBy(_.discountType).foreach {
      case (discountType, discounts) => discountType match {
        case DiscountType.PRICE =>
          val totalDiscount = discounts.map(_.discount).sum
          discountedModel = discountedModel.copy(price = (price * (1 - totalDiscount)).toInt)
        case DiscountType.CONSTRUCTION_TIME =>
          var totalDiscount = discounts.map(_.discount).sum
          totalDiscount = Math.min(1, totalDiscount)
          discountedModel = discountedModel.copy(constructionTime = (constructionTime * (1 - totalDiscount)).toInt)
      }
    }
    discountedModel
  }

  val purchasableWithRelationship = (relationship : Int) => {
    relationship >= Model.BUY_RELATIONSHIP_THRESHOLD
  }
}

object Model {
  val BUY_RELATIONSHIP_THRESHOLD = 0

  def fromId(id : Int) = {
    val modelWithJustId = Model("Unknown", "Unknown", 0, 0, 0, 0, 0, 0, 0, 0, 0, Manufacturer("Unknown", countryCode = ""), runwayRequirement = 0)
    modelWithJustId.id = id
    modelWithJustId
  }

  object Type extends Enumeration {
    type Type = Value
    val AIRSHIP, HELICOPTER, PROPELLER_SMALL, PROPELLER_MEDIUM, SMALL, REGIONAL, MEDIUM, MEDIUM_XL, LARGE, EXTRA_LARGE, JUMBO, SUPERSONIC = Value

    val label: Type => String = {
      case AIRSHIP => "Airship"
      case HELICOPTER => "Helicopter"
      case PROPELLER_SMALL => "Small Prop"
      case PROPELLER_MEDIUM => "Regional Prop"
      case SMALL => "Small Jet"
      case REGIONAL => "Regional Jet"
      case MEDIUM => "Narrow-body"
      case MEDIUM_XL => "Narrow-body XL"
      case LARGE => "Wide-body"
      case EXTRA_LARGE => "Wide-body XL"
      case JUMBO => "Jumbo Jet"
      case SUPERSONIC => "Supersonic"
    }

    val size: Type => Double = {
      case HELICOPTER => 0.02
      case PROPELLER_SMALL => 0.05
      case PROPELLER_MEDIUM => 0.09
      case SMALL => 0.05
      case REGIONAL => 0.09
      case MEDIUM => 0.14
      case MEDIUM_XL => 0.18
      case AIRSHIP => 0.19
      case SUPERSONIC => 0.24
      case LARGE => 0.20
      case EXTRA_LARGE => 0.25
      case JUMBO => 0.3
    }
  }

  object Category extends Enumeration {
    type Category = Value
    val SPECIAL, SMALL, REGIONAL, MEDIUM, LARGE, EXTRAORDINARY = Value
    val grouping: Map[Model.Category.Value, List[Type.Value]] = Map(
      SMALL -> List(Type.SMALL, Type.PROPELLER_SMALL),
      REGIONAL -> List(Type.REGIONAL, Type.PROPELLER_MEDIUM),
      MEDIUM -> List(Type.MEDIUM, Type.MEDIUM_XL),
      LARGE -> List(Type.LARGE, Type.EXTRA_LARGE),
      EXTRAORDINARY -> List(Type.JUMBO, Type.SUPERSONIC),
      SPECIAL -> List(Type.AIRSHIP, Type.HELICOPTER),
    )

    val fromType: Type.Value => Model.Category.Value = (airplaneType : Type.Value) => {
      grouping.find(_._2.contains(airplaneType)).map(_._1).getOrElse(Model.Category.EXTRAORDINARY)
    }

    val capacityRange : Map[Category.Value, (Int, Int)]= {
      AirplaneModelCache.allModels.values.groupBy(_.category).view.mapValues { models =>
        val sortedByCapacity = models.toList.sortBy(_.capacity)
        (sortedByCapacity.head.capacity, sortedByCapacity.last.capacity)
      }.toMap
    }

    val speedRange: Map[Category.Value, (Int, Int)] = {
      AirplaneModelCache.allModels.values.groupBy(_.category).view.mapValues { models =>
        val sortedBySpeed = models.toList.sortBy(_.speed)
        (sortedBySpeed.head.speed, sortedBySpeed.last.speed)
      }.toMap
    }

    def getCapacityRange(category: Category.Value): (Int, Int) = {
      capacityRange.getOrElse(category, (0, 0))
    }

    def getSpeedRange(category: Category.Value): (Int, Int) = {
      speedRange.getOrElse(category, (0, 0))
    }

  }

  val models = List(
Model("Airbus A220-100",	"Airbus A220",	135,	135,	7,	395,	828,	6390,	103118000,	1820,	21,	Manufacturer("Airbus",	countryCode="CA"),	1463,	imageUrl ="https://www.norebbo.com/2016/02/bombardier-cs100-blank-illustration-templates/"),
Model("Airbus A220-300",	"Airbus A220",	160,	160,	8,	524,	828,	6700,	137522000,	1820,	21,	Manufacturer("Airbus",	countryCode="CA"),	1890,	imageUrl ="https://www.norebbo.com/2016/02/bombardier-cs300-blank-illustration-templates/"),
Model("Airbus A220-500",	"Airbus A220",	190,	190,	8,	640,	828,	6100,	175780000,	1820,	30,	Manufacturer("Airbus",	countryCode="CA"),	2000,	imageUrl =""),
Model("Airbus A300-600",	"Airbus A300/A310",	266,	266,	5,	1185,	833,	7500,	112236000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2400,	imageUrl ="https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A300B4",	"Airbus A300/A310",	345,	345,	5,	1450,	847,	5375,	153784000,	1820,	48,	Manufacturer("Airbus",	countryCode="NL"),	1950,	imageUrl ="https://www.norebbo.com/2018/11/airbus-a300b4-600r-blank-illustration-templates-with-general-electric-engines/"),
Model("Airbus A310-200",	"Airbus A300/A310",	240,	240,	6,	989,	850,	6400,	140342000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	1860,	imageUrl ="https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A310-300",	"Airbus A300/A310",	240,	240,	6,	989,	850,	8050,	148802000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2000,	imageUrl ="https://www.norebbo.com/2015/07/airbus-a310-300-blank-illustration-templates/"),
Model("Airbus A318",	"Airbus A320",	136,	136,	6,	480,	829,	7800,	64202000,	1820,	12,	Manufacturer("Airbus",	countryCode="NL"),	1780,	imageUrl ="https://www.norebbo.com/airbus-a318-blank-illustration-templates-with-pratt-whitney-and-cfm56-engines/"),
Model("Airbus A319",	"Airbus A320",	160,	160,	6,	599,	830,	6940,	85728000,	1820,	24,	Manufacturer("Airbus",	countryCode="NL"),	1850,	imageUrl ="https://www.norebbo.com/2014/05/airbus-a319-blank-illustration-templates/"),
Model("Airbus A319neo",	"Airbus A320",	160,	160,	7,	499,	828,	6850,	135830000,	1820,	30,	Manufacturer("Airbus",	countryCode="NL"),	2164,	imageUrl ="https://www.norebbo.com/2017/09/airbus-a319-neo-blank-illustration-templates/"),
Model("Airbus A320",	"Airbus A320",	195,	195,	6,	754,	828,	6150,	96538000,	1820,	30,	Manufacturer("Airbus",	countryCode="NL"),	2050,	imageUrl ="https://www.norebbo.com/2013/08/airbus-a320-blank-illustration-templates/"),
Model("Airbus A320neo",	"Airbus A320",	195,	195,	7,	690,	833,	6500,	145888000,	1820,	36,	Manufacturer("Airbus",	countryCode="NL"),	2100,	imageUrl ="https://www.norebbo.com/2017/08/airbus-a320-neo-blank-illustration-templates/"),
Model("Airbus A321",	"Airbus A320",	236,	236,	6,	990,	830,	5930,	118346000,	1820,	36,	Manufacturer("Airbus",	countryCode="NL"),	2210,	imageUrl ="https://www.norebbo.com/2014/03/airbus-a321-blank-illustration-templates/"),
Model("Airbus A321neo",	"Airbus A320",	244,	244,	7,	854,	828,	6850,	189410000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	1988,	imageUrl ="https://www.norebbo.com/2017/09/airbus-a321-neo-blank-illustration-templates/"),
Model("Airbus A321neoLR",	"Airbus A320",	244,	182,	7,	832,	828,	7400,	198810000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2300,	imageUrl ="https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A321neoXLR",	"Airbus A320",	244,	154,	7,	800,	828,	8700,	205390000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2400,	imageUrl ="https://www.norebbo.com/2018/10/airbus-a321neo-lr-long-range-blank-illustration-templates/"),
Model("Airbus A330-200",	"Airbus A330",	406,	406,	6,	1726,	871,	11300,	249194000,	1820,	36,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2016/02/airbus-a330-200-blank-illustration-templates-with-pratt-whitney-engines/"),
Model("Airbus A330-300",	"Airbus A330",	440,	440,	6,	1998,	871,	13400,	262824000,	1820,	36,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2016/02/airbus-a330-300-blank-illustration-templates-with-all-three-engine-options/"),
Model("Airbus A330-800neo",	"Airbus A330",	406,	374,	7,	1707,	918,	13900,	373556000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2018/06/airbus-a330-800-neo-blank-illustration-templates/"),
Model("Airbus A330-900neo",	"Airbus A330",	460,	348,	7,	1930,	918,	12130,	411344000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2770,	imageUrl ="https://www.norebbo.com/2018/06/airbus-a330-900-neo-blank-illustration-templates/"),
Model("Airbus A340-200",	"Airbus A340",	315,	315,	5,	1480,	880,	14800,	152280000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2900,	imageUrl ="https://www.norebbo.com/2019/01/airbus-a340-200-side-view/"),
Model("Airbus A340-300",	"Airbus A340",	350,	350,	5,	1665,	880,	13350,	163466000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	3000,	imageUrl ="https://www.norebbo.com/2016/04/airbus-340-300-and-a340-300x-blank-illustration-templates/"),
Model("Airbus A340-500",	"Airbus A340",	375,	375,	5,	1792,	871,	17000,	197682000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	3350,	imageUrl ="https://www.norebbo.com/2016/08/airbus-a340-500-blank-illustration-templates/"),
Model("Airbus A340-600",	"Airbus A340",	440,	440,	5,	2288,	905,	13900,	246092000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	3400,	imageUrl ="https://www.norebbo.com/2016/11/airbus-a340-600-blank-illustration-templates/"),
Model("Airbus A350-1000",	"Airbus A350",	475,	475,	8,	2205,	910,	15050,	467650000,	1820,	60,	Manufacturer("Airbus",	countryCode="NL"),	2600,	imageUrl ="https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-1000 Sunrise",	"Airbus A350",	475,	212,	8,	1952,	910,	18000,	475170000,	1820,	60,	Manufacturer("Airbus",	countryCode="NL"),	2700,	imageUrl ="https://www.norebbo.com/2015/11/airbus-a350-1000-blank-illustration-templates/"),
Model("Airbus A350-900",	"Airbus A350",	440,	440,	8,	1956,	903,	15000,	420556000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2600,	imageUrl ="https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A350-900ULR",	"Airbus A350",	440,	198,	8,	1760,	910,	17960,	466616000,	1820,	54,	Manufacturer("Airbus",	countryCode="NL"),	2600,	imageUrl ="https://www.norebbo.com/2013/07/airbus-a350-900-blank-illustration-templates/"),
Model("Airbus A380-800",	"Airbus A380",	880,	853,	7,	4679,	925,	15700,	590038000,	1820,	81,	Manufacturer("Airbus",	countryCode="NL"),	3000,	imageUrl ="https://www.norebbo.com/2013/06/airbus-a380-800-blank-illustration-templates/"),
Model("Airbus H225 Eurocopter",	"Eurocopter",	28,	19,	7,	39,	262,	857,	7708000,	1560,	12,	Manufacturer("Airbus",	countryCode="NL"),	1,	imageUrl =""),
Model("Airbus ZeroE Turbofan",	"Airbus ZE",	175,	175,	9,	118,	795,	2000,	196742000,	1040,	30,	Manufacturer("Airbus",	countryCode="NL"),	2100,	imageUrl =""),
Model("Airbus ZeroE Turboprop",	"Airbus ZE",	85,	85,	9,	43,	674,	1000,	82532000,	1040,	36,	Manufacturer("Airbus",	countryCode="NL"),	2000,	imageUrl =""),
Model("Airlander 10 Cruise",	"HAV Airlander",	324,	84,	10,	6,	128,	3700,	103964000,	780,	30,	Manufacturer("HAV Airlander",	countryCode="GB"),	100,	imageUrl =""),
Model("Airlander 10 Ferry",	"HAV Airlander",	324,	120,	9,	14,	116,	1600,	72568000,	780,	30,	Manufacturer("HAV Airlander",	countryCode="GB"),	100,	imageUrl =""),
Model("Airlander 50",	"HAV Airlander",	540,	220,	9,	28,	114,	2200,	123610000,	780,	30,	Manufacturer("HAV Airlander",	countryCode="GB"),	100,	imageUrl =""),
Model("Antonov An-10A",	"Antonov An",	132,	132,	1,	447,	570,	4075,	18988000,	1456,	12,	Manufacturer("Antonov",	countryCode="UA"),	975,	imageUrl =""),
Model("Antonov An-148",	"Antonov An",	75,	75,	1,	233,	835,	5300,	14382000,	1040,	9,	Manufacturer("Antonov",	countryCode="UA"),	1900,	imageUrl =""),
Model("Antonov An-158",	"Antonov An",	102,	102,	1,	322,	835,	3500,	18518000,	1040,	9,	Manufacturer("Antonov",	countryCode="UA"),	1885,	imageUrl =""),
Model("Antonov An-24",	"Antonov An",	50,	50,	1,	68,	450,	1800,	10904000,	1456,	0,	Manufacturer("Antonov",	countryCode="UA"),	970,	imageUrl =""),
Model("Antonov An-72",	"Antonov An",	52,	52,	3,	140,	700,	4325,	9024000,	1456,	0,	Manufacturer("Antonov",	countryCode="UA"),	700,	imageUrl =""),
Model("ATR 42-400",	"ATR-Regional",	48,	48,	4,	60,	484,	1326,	12690000,	1040,	0,	Manufacturer("ATR",	countryCode="FR"),	1050,	imageUrl ="https://www.norebbo.com/atr-42-blank-illustration-templates/"),
Model("ATR 42-600S",	"ATR-Regional",	48,	48,	5,	60,	535,	1260,	15040000,	1040,	0,	Manufacturer("ATR",	countryCode="FR"),	750,	imageUrl ="https://www.norebbo.com/atr-42-blank-illustration-templates/"),
Model("ATR 72-200",	"ATR-Regional",	66,	66,	5,	87,	517,	1464,	18800000,	1040,	0,	Manufacturer("ATR",	countryCode="FR"),	1211,	imageUrl ="https://www.norebbo.com/2017/04/atr-72-blank-illustration-templates/"),
Model("ATR 72-600",	"ATR-Regional",	72,	72,	5,	96,	510,	1403,	23218000,	1040,	6,	Manufacturer("ATR",	countryCode="FR"),	1279,	imageUrl ="https://www.norebbo.com/2017/04/atr-72-blank-illustration-templates/"),
Model("Aurora D8",	"Aurora D",	229,	180,	9,	530,	937,	5600,	369702000,	1820,	54,	Manufacturer("Aurora Flight Sciences",	countryCode="US"),	2300,	imageUrl =""),
Model("BAe 146-100",	"BAe 146",	82,	82,	5,	215,	789,	3650,	24722000,	1560,	3,	Manufacturer("BAe",	countryCode="GB"),	1195,	imageUrl ="https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-200",	"BAe 146",	93,	93,	5,	265,	789,	3650,	27918000,	1560,	3,	Manufacturer("BAe",	countryCode="GB"),	1390,	imageUrl ="https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe 146-300",	"BAe 146",	113,	113,	5,	335,	789,	3650,	38728000,	1560,	3,	Manufacturer("BAe",	countryCode="GB"),	1535,	imageUrl ="https://www.norebbo.com/2018/11/british-aerospace-bae-146-200-avro-rj85-blank-illustration-templates/"),
Model("BAe Jetstream 41",	"BAe Jetstream",	29,	29,	6,	38,	482,	1433,	8930000,	1820,	0,	Manufacturer("BAe",	countryCode="GB"),	1524,	imageUrl ="https://www.norebbo.com/british-aerospace-jetstream-41-blank-illustration-templates/"),
Model("BAe Jetstream 61",	"BAe Jetstream",	56,	56,	6,	95,	496,	1825,	20868000,	1820,	6,	Manufacturer("BAe",	countryCode="GB"),	1200,	imageUrl ="https://www.norebbo.com/british-aerospace-jetstream-41-blank-illustration-templates/"),
Model("Boeing 2707",	"Boeing 2707",	554,	277,	9,	11100,	3300,	5900,	836224000,	1664,	63,	Manufacturer("Boeing",	countryCode="US"),	3800,	imageUrl =""),
Model("Boeing 307 Stratoliner",	"Post-War Props",	60,	60,	1,	126,	357,	3850,	2726000,	1508,	0,	Manufacturer("Boeing",	countryCode="US"),	620,	imageUrl =""),
Model("Boeing 377 Stratocruiser",	"Post-War Props",	117,	117,	2,	383,	480,	6760,	19270000,	1456,	6,	Manufacturer("Boeing",	countryCode="US"),	1000,	imageUrl =""),
Model("Boeing 707",	"Boeing 707",	194,	194,	3,	995,	1003,	6700,	40232000,	2496,	0,	Manufacturer("Boeing",	countryCode="US"),	2700,	imageUrl ="https://www.norebbo.com/boeing-707-320c-blank-illustration-templates/"),
Model("Boeing 717-200",	"DC-9",	134,	134,	5,	415,	811,	2645,	43522000,	1820,	6,	Manufacturer("Boeing",	countryCode="US"),	2050,	imageUrl ="https://www.norebbo.com/2017/06/boeing-717-200-blank-illustration-templates/"),
Model("Boeing 727-100",	"Boeing 727",	131,	131,	4,	610,	960,	4170,	35438000,	2184,	18,	Manufacturer("Boeing",	countryCode="US"),	1750,	imageUrl =""),
Model("Boeing 727-200",	"Boeing 727",	189,	189,	5,	831,	811,	4020,	61852000,	2184,	36,	Manufacturer("Boeing",	countryCode="US"),	1800,	imageUrl ="https://www.norebbo.com/2018/03/boeing-727-200-blank-illustration-templates/"),
Model("Boeing 737 MAX 10",	"Boeing 737",	230,	204,	7,	750,	830,	6500,	185932000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2700,	imageUrl ="https://www.norebbo.com/2019/01/737-10-max-side-view/"),
Model("Boeing 737 MAX 7",	"Boeing 737",	172,	153,	7,	550,	830,	6500,	128592000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	2050,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-max-7-blank-illustration-templates/"),
Model("Boeing 737 MAX 8",	"Boeing 737",	210,	178,	7,	664,	830,	6500,	159330000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	2500,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 8-200",	"Boeing 737",	200,	200,	6,	685,	839,	5200,	138462000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	2500,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-max-8-blank-illustration-templates/"),
Model("Boeing 737 MAX 9",	"Boeing 737",	220,	193,	7,	722,	839,	6570,	169952000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/2018/05/boeing-737-9-max-blank-illustration-templates/"),
Model("Boeing 737-100",	"Boeing 737",	124,	124,	3,	505,	780,	3440,	24628000,	1976,	12,	Manufacturer("Boeing",	countryCode="US"),	1800,	imageUrl ="https://www.norebbo.com/2018/10/boeing-737-100-blank-illustration-templates/"),
Model("Boeing 737-200",	"Boeing 737",	136,	136,	4,	550,	780,	4200,	36472000,	1976,	18,	Manufacturer("Boeing",	countryCode="US"),	1859,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-200-blank-illustration-templates/"),
Model("Boeing 737-300",	"Boeing 737",	144,	144,	4,	580,	800,	4400,	46154000,	1976,	24,	Manufacturer("Boeing",	countryCode="US"),	1940,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-300-blank-illustration-templates/"),
Model("Boeing 737-400",	"Boeing 737",	168,	168,	4,	670,	800,	5000,	64484000,	1976,	24,	Manufacturer("Boeing",	countryCode="US"),	2540,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-400-blank-illustration-templates/"),
Model("Boeing 737-500",	"Boeing 737",	132,	132,	5,	510,	800,	5200,	56118000,	1976,	18,	Manufacturer("Boeing",	countryCode="US"),	1830,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-500-blank-illustration-templates-with-and-without-blended-winglets/"),
Model("Boeing 737-600",	"Boeing 737",	130,	130,	5,	489,	834,	7200,	54144000,	1976,	24,	Manufacturer("Boeing",	countryCode="US"),	1878,	imageUrl ="https://www.norebbo.com/2018/09/boeing-737-600-blank-illustration-templates/"),
Model("Boeing 737-700",	"Boeing 737",	149,	149,	5,	592,	834,	7630,	64954000,	1820,	24,	Manufacturer("Boeing",	countryCode="US"),	2042,	imageUrl ="https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-700ER",	"Boeing 737",	149,	88,	6,	525,	834,	10695,	82344000,	1820,	24,	Manufacturer("Boeing",	countryCode="US"),	2042,	imageUrl ="https://www.norebbo.com/2014/04/boeing-737-700-blank-illustration-templates/"),
Model("Boeing 737-800",	"Boeing 737",	180,	180,	6,	738,	842,	5436,	94188000,	1820,	30,	Manufacturer("Boeing",	countryCode="US"),	2316,	imageUrl ="https://www.norebbo.com/2012/11/boeing-737-800-blank-illustration-templates/"),
Model("Boeing 737-900",	"Boeing 737",	215,	215,	6,	875,	842,	5436,	113552000,	1820,	30,	Manufacturer("Boeing",	countryCode="US"),	3000,	imageUrl ="https://www.norebbo.com/2014/08/boeing-737-900-blank-illustration-templates/"),
Model("Boeing 737-900ER",	"Boeing 737",	220,	220,	6,	868,	844,	5460,	124362000,	1820,	36,	Manufacturer("Boeing",	countryCode="US"),	3000,	imageUrl ="https://www.norebbo.com/2016/07/boeing-737-900er-with-split-scimitar-winglets-blank-illustration-templates/"),
Model("Boeing 747-100",	"Boeing 747",	550,	550,	5,	3105,	907,	8530,	248818000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3250,	imageUrl ="https://www.norebbo.com/2019/07/boeing-747-100-side-view/"),
Model("Boeing 747-200",	"Boeing 747",	550,	550,	5,	3025,	907,	12150,	262918000,	1820,	60,	Manufacturer("Boeing",	countryCode="US"),	3300,	imageUrl ="https://www.norebbo.com/2019/08/boeing-747-200-side-view/"),
Model("Boeing 747-300",	"Boeing 747",	660,	400,	6,	3602,	939,	11720,	363686000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	2955,	imageUrl ="https://www.norebbo.com/boeing-747-300-side-view/"),
Model("Boeing 747-400",	"Boeing 747",	660,	416,	6,	3615,	943,	13490,	382486000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3260,	imageUrl =""),
Model("Boeing 747-400D",	"Boeing 747",	660,	660,	5,	3845,	943,	10490,	296758000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3260,	imageUrl =""),
Model("Boeing 747-400ER",	"Boeing 747",	660,	416,	6,	3625,	943,	14045,	399406000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3300,	imageUrl ="https://www.norebbo.com/2013/09/boeing-747-400-blank-illustration-templates/"),
Model("Boeing 747-8i",	"Boeing 747",	718,	605,	7,	3690,	933,	14320,	560992000,	1820,	72,	Manufacturer("Boeing",	countryCode="US"),	3100,	imageUrl ="https://www.norebbo.com/2015/12/boeing-747-8i-blank-illustration-templates/"),
Model("Boeing 747SP",	"Boeing 747",	400,	248,	6,	2090,	994,	10800,	249570000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2820,	imageUrl ="https://www.norebbo.com/2019/08/boeing-747sp-side-view/"),
Model("Boeing 757-100",	"Boeing 757",	160,	160,	5,	660,	854,	8250,	66176000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	1910,	imageUrl =""),
Model("Boeing 757-200",	"Boeing 757",	239,	239,	5,	1023,	854,	7250,	83660000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	2070,	imageUrl ="https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-200ER",	"Boeing 757",	239,	239,	5,	950,	850,	9170,	105280000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	2070,	imageUrl ="https://www.norebbo.com/2015/01/boeing-757-200-blank-illustration-templates/"),
Model("Boeing 757-300",	"Boeing 757",	295,	295,	5,	1426,	850,	6421,	116842000,	1976,	54,	Manufacturer("Boeing",	countryCode="US"),	2377,	imageUrl ="https://www.norebbo.com/2017/03/boeing-757-300-blank-illustration-templates/"),
Model("Boeing 767-200",	"Boeing 767",	265,	265,	5,	1285,	851,	7200,	81122000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	1900,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-200ER",	"Boeing 767",	265,	216,	5,	1165,	896,	12200,	112142000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2480,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-300",	"Boeing 767",	299,	299,	5,	1385,	851,	7200,	147768000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-300ER",	"Boeing 767",	299,	261,	6,	1315,	896,	11093,	178882000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2650,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-200-blank-illustration-templates/"),
Model("Boeing 767-400ER",	"Boeing 767",	409,	272,	6,	1830,	896,	10415,	227010000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3290,	imageUrl ="https://www.norebbo.com/2014/07/boeing-767-400-blank-illustration-templates/"),
Model("Boeing 777-200",	"Boeing 777",	440,	440,	6,	2050,	896,	9700,	300424000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2440,	imageUrl ="https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200ER",	"Boeing 777",	440,	301,	7,	1975,	896,	13080,	346672000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3280,	imageUrl ="https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-200LR",	"Boeing 777",	440,	301,	7,	1925,	896,	15843,	358892000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2012/12/boeing-777-200-blank-illustration-templates/"),
Model("Boeing 777-300",	"Boeing 777",	550,	550,	6,	2925,	945,	11121,	348458000,	1820,	66,	Manufacturer("Boeing",	countryCode="US"),	3230,	imageUrl ="https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-300ER",	"Boeing 777",	550,	365,	7,	2895,	945,	13649,	426572000,	1820,	66,	Manufacturer("Boeing",	countryCode="US"),	3050,	imageUrl ="https://www.norebbo.com/2014/03/boeing-777-300-blank-illustration-templates/"),
Model("Boeing 777-8",	"Boeing 777",	440,	395,	8,	1778,	896,	16090,	476016000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	3050,	imageUrl ="https://www.norebbo.com/2019/12/boeing-777-8-side-view/"),
Model("Boeing 777-9",	"Boeing 777",	520,	426,	8,	2069,	896,	13940,	602164000,	1820,	66,	Manufacturer("Boeing",	countryCode="US"),	3050,	imageUrl ="https://www.norebbo.com/2019/12/boeing-777-9-side-view/"),
Model("Boeing 787-10 Dreamliner",	"Boeing 787",	440,	440,	7,	1903,	903,	11750,	365472000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2017/06/boeing-787-10-blank-illustration-templates/"),
Model("Boeing 787-10ER",	"Boeing 787",	440,	280,	7,	1860,	903,	13750,	388972000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2900,	imageUrl ="https://www.norebbo.com/2017/06/boeing-787-10-blank-illustration-templates/"),
Model("Boeing 787-8 Dreamliner",	"Boeing 787",	359,	359,	7,	1489,	907,	13621,	285196000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/2013/02/boeing-787-8-blank-illustration-templates/"),
Model("Boeing 787-9 Dreamliner",	"Boeing 787",	420,	420,	7,	1853,	903,	14010,	314900000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2800,	imageUrl ="https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 787-9ER",	"Boeing 787",	420,	265,	7,	1762,	903,	15400,	352500000,	1820,	54,	Manufacturer("Boeing",	countryCode="US"),	2900,	imageUrl ="https://www.norebbo.com/2014/04/boeing-787-9-blank-illustration-templates/"),
Model("Boeing 797-6",	"Boeing 797",	225,	225,	8,	759,	890,	9260,	238008000,	1820,	60,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing 797-7",	"Boeing 797",	275,	275,	8,	952,	890,	8330,	298544000,	1820,	60,	Manufacturer("Boeing",	countryCode="US"),	2600,	imageUrl ="https://www.norebbo.com/boeing-797-side-view/"),
Model("Boeing Vertol 107-II",	"Boeing Vertol",	28,	28,	4,	47,	265,	1020,	3572000,	1300,	6,	Manufacturer("Boeing",	countryCode="US"),	1,	imageUrl =""),
Model("Boeing Vertol 234",	"Boeing Vertol",	44,	44,	4,	70,	269,	1010,	7614000,	1300,	6,	Manufacturer("Boeing",	countryCode="US"),	1,	imageUrl =""),
Model("Bombardier CRJ100",	"Bombardier CRJ",	50,	50,	7,	92,	830,	2250,	31490000,	1820,	0,	Manufacturer("Bombardier",	countryCode="CA"),	1920,	imageUrl ="https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ1000",	"Bombardier CRJ",	104,	104,	7,	299,	870,	3004,	63732000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	2120,	imageUrl ="https://www.norebbo.com/2019/06/bombardier-crj-1000-side-view/"),
Model("Bombardier CRJ200",	"Bombardier CRJ",	50,	50,	7,	92,	830,	3150,	33370000,	1820,	0,	Manufacturer("Bombardier",	countryCode="CA"),	1920,	imageUrl ="https://www.norebbo.com/2015/04/bombardier-canadair-regional-jet-200-blank-illustration-templates/"),
Model("Bombardier CRJ700",	"Bombardier CRJ",	78,	78,	7,	172,	828,	3045,	52546000,	1820,	6,	Manufacturer("Bombardier",	countryCode="CA"),	1605,	imageUrl ="https://www.norebbo.com/2015/05/bombardier-canadair-regional-jet-700-blank-illustration-templates/"),
Model("Bombardier CRJ900",	"Bombardier CRJ",	90,	90,	7,	211,	870,	2876,	56964000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	1939,	imageUrl ="https://www.norebbo.com/2016/07/bombardier-canadair-regional-jet-900-blank-illustration-templates/"),
Model("Bombardier Global 5000",	"Modern Business Jet",	40,	16,	10,	50,	934,	9630,	37412000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	1689,	imageUrl ="https://www.norebbo.com/bombardier-global-5000-blank-illustration-templates/"),
Model("Bombardier Global 7500",	"Modern Business Jet",	48,	19,	10,	67,	1080,	14260,	52264000,	1820,	12,	Manufacturer("Bombardier",	countryCode="CA"),	1768,	imageUrl ="https://www.norebbo.com/bombardier-global-7500-side-view/"),
Model("Boom Overture",	"Boom Overture",	160,	80,	10,	1996,	1800,	7870,	395176000,	1664,	150,	Manufacturer("Boom Technology",	countryCode="US"),	3048,	imageUrl =""),
Model("CASA C-212 Aviocar",	"CASA",	26,	26,	4,	32,	354,	2680,	3384000,	1560,	0,	Manufacturer("CASA",	countryCode="ES"),	600,	imageUrl =""),
Model("CASA CN-235",	"CASA",	40,	40,	4,	56,	460,	3658,	7708000,	1560,	0,	Manufacturer("CASA",	countryCode="ES"),	1204,	imageUrl =""),
Model("Cessna Caravan",	"Cessna",	16,	16,	4,	15,	355,	2400,	3008000,	1820,	0,	Manufacturer("Cessna",	countryCode="US"),	762,	imageUrl ="https://www.norebbo.com/2017/06/cessna-208-grand-caravan-blank-illustration-templates/"),
Model("Cessna Citation X",	"Cessna",	30,	12,	10,	34,	900,	6050,	28388000,	1820,	12,	Manufacturer("Cessna",	countryCode="US"),	1600,	imageUrl ="https://www.norebbo.com/cessna-citation-x-template/"),
Model("Comac ARJ21",	"Comac ARJ21",	90,	90,	4,	235,	828,	2200,	35062000,	1300,	12,	Manufacturer("COMAC",	countryCode="CN"),	1700,	imageUrl =""),
Model("Comac C919",	"Comac C919",	168,	168,	5,	634,	834,	4075,	70970000,	1300,	24,	Manufacturer("COMAC",	countryCode="CN"),	2000,	imageUrl ="https://www.norebbo.com/comac-c919-side-view/"),
Model("Comac C929-500",	"Comac C929",	348,	348,	6,	1608,	908,	14000,	182548000,	1560,	36,	Manufacturer("COMAC",	countryCode="CN"),	2700,	imageUrl =""),
Model("Comac C929-600",	"Comac C929",	405,	405,	6,	1950,	908,	12000,	211688000,	1560,	36,	Manufacturer("COMAC",	countryCode="CN"),	2800,	imageUrl =""),
Model("Comac C929-700",	"Comac C929",	440,	440,	6,	2110,	908,	10000,	253988000,	1560,	36,	Manufacturer("COMAC",	countryCode="CN"),	2900,	imageUrl =""),
Model("Comac C939",	"Comac C939",	460,	460,	6,	2188,	908,	14000,	258500000,	1560,	45,	Manufacturer("COMAC",	countryCode="CN"),	2800,	imageUrl =""),
Model("Concorde",	"Concorde",	192,	128,	9,	3390,	2158,	7223,	367352000,	1820,	90,	Manufacturer("BAe",	countryCode="GB"),	3600,	imageUrl ="https://www.norebbo.com/aerospatiale-bac-concorde-blank-illustration-templates/"),
Model("Dassault Falcon 50",	"60s Business Jet",	28,	14,	10,	57,	883,	5660,	17202000,	1456,	12,	Manufacturer("Dassault",	countryCode="FR"),	1524,	imageUrl ="https://www.norebbo.com/dassault-falcon-50/"),
Model("Dassault Mercure",	"Dassault",	162,	162,	2,	540,	926,	2084,	61570000,	1820,	18,	Manufacturer("Dassault Aviation",	countryCode="FR"),	2100,	imageUrl =""),
Model("De Havilland Canada DHC-7-100",	"De Havilland Canada DHC",	50,	50,	4,	66,	428,	1300,	17484000,	1820,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	620,	imageUrl =""),
Model("De Havilland Canada DHC-8-100",	"De Havilland Canada DHC",	39,	39,	5,	55,	448,	1889,	14194000,	1820,	3,	Manufacturer("De Havilland Canada",	countryCode="CA"),	950,	imageUrl ="https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland Canada DHC-8-200",	"De Havilland Canada DHC",	39,	39,	5,	55,	448,	2084,	15604000,	1820,	3,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1000,	imageUrl ="https://www.norebbo.com/2018/01/de-havilland-dhc-8-200-dash-8-blank-illustration-templates/"),
Model("De Havilland Canada DHC-8-300",	"De Havilland Canada DHC",	50,	50,	5,	74,	450,	1711,	19928000,	1820,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1085,	imageUrl ="https://www.norebbo.com/2018/05/de-havilland-dhc-8-300-blank-illustration-templates/"),
Model("De Havilland Canada DHC-8-400",	"De Havilland Canada DHC",	68,	68,	5,	158,	667,	1980,	31208000,	1560,	6,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1085,	imageUrl ="https://www.norebbo.com/2018/05/de-havilland-dhc-8-300-blank-illustration-templates/"),
Model("De Havilland Canada Q400",	"De Havilland Canada DHC",	82,	82,	5,	175,	562,	2040,	33558000,	1560,	12,	Manufacturer("De Havilland Canada",	countryCode="CA"),	1885,	imageUrl ="https://www.norebbo.com/2015/08/bombardier-dhc-8-402-q400-blank-illustration-templates/"),
Model("Dornier 1128",	"Dornier",	130,	130,	8,	464,	923,	3800,	79806000,	1300,	18,	Manufacturer("Dornier",	countryCode="DE"),	1550,	imageUrl =""),
Model("Dornier 328-110",	"Dornier",	33,	33,	8,	60,	620,	1310,	18894000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1088,	imageUrl ="https://www.norebbo.com/2019/01/dornier-328-110-blank-illustration-templates/"),
Model("Dornier 328JET",	"Dornier",	44,	44,	8,	74,	740,	1665,	28388000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1367,	imageUrl ="https://www.norebbo.com/2019/01/fairchild-dornier-328jet-illustrations/"),
Model("Dornier 528",	"Dornier",	65,	65,	8,	178,	1000,	3000,	40702000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1363,	imageUrl =""),
Model("Dornier 728",	"Dornier",	80,	80,	8,	253,	1000,	3300,	48598000,	1820,	9,	Manufacturer("Dornier",	countryCode="DE"),	1463,	imageUrl =""),
Model("Dornier 928",	"Dornier",	112,	112,	8,	399,	951,	3600,	61288000,	1300,	12,	Manufacturer("Dornier",	countryCode="DE"),	1513,	imageUrl =""),
Model("Douglas DC-10-10",	"DC-10",	410,	410,	3,	1835,	876,	6500,	130566000,	1820,	18,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2700,	imageUrl ="https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("Douglas DC-10-30",	"DC-10",	410,	410,	3,	1850,	886,	9400,	157826000,	1820,	24,	Manufacturer("McDonnell Douglas",	countryCode="US"),	3200,	imageUrl ="https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("Douglas DC-10-40",	"DC-10",	410,	410,	4,	1900,	886,	12392,	159612000,	1820,	30,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2900,	imageUrl ="https://www.norebbo.com/mcdonnell-douglas-dc-10-30-blank-templates/"),
Model("Douglas DC-3",	"Post-War Props",	32,	32,	0,	51,	333,	1800,	0,	3224,	0,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	701,	imageUrl =""),
Model("Douglas DC-8-10",	"DC-8",	177,	177,	2,	929,	895,	6960,	22560000,	1820,	6,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	2680,	imageUrl =""),
Model("Douglas DC-8-72",	"DC-8",	189,	189,	3,	969,	895,	9800,	29046000,	1820,	12,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	2680,	imageUrl =""),
Model("Douglas DC-8-73",	"DC-8",	259,	259,	3,	1340,	895,	8300,	46624000,	1820,	12,	Manufacturer("Douglas Aircraft Company",	countryCode="US"),	2680,	imageUrl ="https://www.norebbo.com/douglas-dc-8-73-and-dc-8-73cf-blank-illustration-templates/"),
Model("Embraer E175-E2",	"Embraer E-Jet E2",	88,	88,	6,	200,	870,	3735,	52264000,	1560,	9,	Manufacturer("Embraer",	countryCode="BR"),	1800,	imageUrl ="https://www.norebbo.com/2019/03/e175-e2-side-view/"),
Model("Embraer E190-E2",	"Embraer E-Jet E2",	106,	106,	6,	260,	870,	5278,	64578000,	1560,	12,	Manufacturer("Embraer",	countryCode="BR"),	1450,	imageUrl ="https://www.norebbo.com/2019/03/e190-e2-blank-side-view/"),
Model("Embraer E195-E2",	"Embraer E-Jet E2",	146,	146,	6,	421,	833,	4800,	78114000,	1560,	18,	Manufacturer("Embraer",	countryCode="BR"),	1970,	imageUrl ="https://www.norebbo.com/2019/03/embraer-e195-e2-side-view/"),
Model("Embraer ERJ135",	"Embraer ERJ",	37,	37,	5,	79,	850,	3241,	10058000,	1560,	3,	Manufacturer("Embraer",	countryCode="BR"),	1580,	imageUrl ="https://www.norebbo.com/2018/05/embraer-erj-135-blank-illustration-templates/"),
Model("Embraer ERJ145",	"Embraer ERJ",	50,	50,	5,	126,	850,	2800,	19082000,	1560,	3,	Manufacturer("Embraer",	countryCode="BR"),	1410,	imageUrl ="https://www.norebbo.com/2018/04/embraer-erj-145-blank-illustration-templates/"),
Model("Embraer ERJ145XR",	"Embraer ERJ",	50,	50,	6,	124,	850,	3700,	21902000,	1560,	0,	Manufacturer("Embraer",	countryCode="BR"),	1720,	imageUrl ="https://www.norebbo.com/2018/04/embraer-erj-145xr-blank-illustration-templates/"),
Model("Embraer ERJ170",	"Embraer ERJ",	72,	72,	5,	212,	870,	3982,	23782000,	1560,	3,	Manufacturer("Embraer",	countryCode="BR"),	1644,	imageUrl ="https://www.norebbo.com/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer ERJ175",	"Embraer ERJ",	78,	78,	5,	235,	870,	4074,	28294000,	1560,	6,	Manufacturer("Embraer",	countryCode="BR"),	2144,	imageUrl ="https://www.norebbo.com/2015/10/embraer-erj-175-templates-with-the-new-style-winglets/"),
Model("Embraer ERJ190",	"Embraer ERJ",	100,	100,	5,	310,	823,	4537,	34404000,	1560,	12,	Manufacturer("Embraer",	countryCode="BR"),	2054,	imageUrl ="https://www.norebbo.com/2015/06/embraer-190-blank-illustration-templates/"),
Model("F27-100",	"Fokker",	44,	44,	3,	61,	460,	1468,	9212000,	1560,	0,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Fokker 100",	"Fokker",	109,	109,	4,	340,	845,	1686,	41172000,	1820,	3,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl ="https://www.norebbo.com/2018/07/fokker-100-f-28-0100-blank-illustration-templates/"),
Model("Fokker 50",	"Fokker",	56,	56,	3,	86,	500,	1700,	13160000,	1560,	3,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Fokker 60",	"Fokker",	64,	64,	3,	115,	515,	1550,	16638000,	1560,	3,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Fokker 60 Jet",	"Fokker",	64,	64,	3,	160,	845,	1400,	22278000,	1560,	6,	Manufacturer("Fokker",	countryCode="NL"),	1550,	imageUrl =""),
Model("Gulfstream G650ER",	"Modern Business Jet",	48,	19,	10,	51,	966,	13890,	60066000,	2080,	12,	Manufacturer("Gulfstream",	countryCode="US"),	1920,	imageUrl ="https://www.norebbo.com/gulfstream-g650er-template/"),
Model("Ilyushin Il-18",	"Ilyushin Il",	120,	120,	0,	483,	625,	6500,	10622000,	1300,	6,	Manufacturer("Ilyushin",	countryCode="RU"),	1350,	imageUrl =""),
Model("Ilyushin Il-62",	"Ilyushin Il",	186,	186,	2,	949,	900,	10000,	28670000,	1456,	18,	Manufacturer("Ilyushin",	countryCode="RU"),	2300,	imageUrl =""),
Model("Ilyushin Il-86",	"Ilyushin Il-96",	320,	320,	3,	1560,	950,	5000,	85070000,	1456,	24,	Manufacturer("Ilyushin",	countryCode="RU"),	2800,	imageUrl =""),
Model("Ilyushin Il-96-300",	"Ilyushin Il-96",	300,	300,	4,	1500,	900,	13500,	69184000,	1456,	27,	Manufacturer("Ilyushin",	countryCode="RU"),	3200,	imageUrl =""),
Model("Ilyushin Il-96-400",	"Ilyushin Il-96",	436,	436,	4,	2198,	900,	14500,	124644000,	1456,	36,	Manufacturer("Ilyushin",	countryCode="RU"),	2600,	imageUrl =""),
Model("Lockheed JetStar",	"60s Business Jet",	26,	11,	9,	52,	920,	4820,	14758000,	1820,	6,	Manufacturer("Lockheed",	countryCode="US"),	1100,	imageUrl =""),
Model("Lockheed L-1011-100",	"Lockheed TriStar",	400,	400,	5,	1882,	962,	4963,	141470000,	1560,	24,	Manufacturer("Lockheed",	countryCode="US"),	2560,	imageUrl =""),
Model("Lockheed L-1011-200",	"Lockheed TriStar",	400,	400,	5,	1892,	969,	6500,	159330000,	1560,	27,	Manufacturer("Lockheed",	countryCode="US"),	2560,	imageUrl =""),
Model("Lockheed L-1011-500",	"Lockheed TriStar",	400,	250,	6,	1868,	986,	9899,	214978000,	1560,	36,	Manufacturer("Lockheed",	countryCode="US"),	2865,	imageUrl ="https://www.norebbo.com/2015/03/lockheed-l-1011-500-blank-illustration-templates/"),
Model("McDonnell Douglas DC-9-10",	"DC-9",	92,	92,	3,	270,	965,	2367,	24534000,	1040,	6,	Manufacturer("McDonnell Douglas",	countryCode="US"),	1816,	imageUrl =""),
Model("McDonnell Douglas DC-9-30",	"DC-9",	115,	115,	4,	345,	804,	2778,	29986000,	1040,	6,	Manufacturer("McDonnell Douglas",	countryCode="US"),	1900,	imageUrl ="https://www.norebbo.com/mcdonnell-douglas-dc-9-30-templates/"),
Model("McDonnell Douglas DC-9-50",	"DC-9",	139,	139,	4,	464,	804,	3030,	39386000,	1040,	6,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2100,	imageUrl ="https://www.norebbo.com/dc-9-50-side-view/"),
Model("McDonnell Douglas MD-11",	"DC-10",	410,	410,	5,	1910,	886,	12455,	209808000,	1820,	36,	Manufacturer("McDonnell Douglas",	countryCode="US"),	3050,	imageUrl =""),
Model("McDonnell Douglas MD-220",	"60s Business Jet",	29,	29,	8,	70,	1020,	4100,	16544000,	1820,	12,	Manufacturer("McDonnell Douglas",	countryCode="US"),	1200,	imageUrl =""),
Model("McDonnell Douglas MD-81",	"DC-9",	146,	146,	4,	425,	811,	4635,	70030000,	1560,	18,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2070,	imageUrl ="https://www.norebbo.com/2015/02/mcdonnell-douglas-md-80-blank-illustration-templates/"),
Model("McDonnell Douglas MD-90",	"DC-9",	160,	160,	5,	530,	811,	3787,	79336000,	1560,	30,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2134,	imageUrl ="https://www.norebbo.com/2018/02/mcdonnell-douglas-md-90-blank-illustration-templates/"),
Model("McDonnell Douglas MD-90ER",	"DC-9",	160,	160,	5,	510,	811,	4143,	85916000,	1560,	30,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2134,	imageUrl ="https://www.norebbo.com/2018/02/mcdonnell-douglas-md-90-blank-illustration-templates/"),
Model("McDonnell Douglas MD-XX",	"DC-10",	440,	380,	5,	1950,	906,	10455,	231052000,	1820,	36,	Manufacturer("McDonnell Douglas",	countryCode="US"),	2964,	imageUrl =""),
Model("Mil Mi-26",	"Mil",	90,	90,	0,	155,	255,	970,	12126000,	1300,	0,	Manufacturer("Mil",	countryCode="RU"),	1,	imageUrl =""),
Model("Mitsubishi MRJ-100",	"Mitsubishi SpaceJet",	88,	88,	7,	195,	900,	3540,	75388000,	2080,	9,	Manufacturer("Mitsubishi",	countryCode="JP"),	1760,	imageUrl =""),
Model("Mitsubishi MRJ-90",	"Mitsubishi SpaceJet",	92,	92,	7,	204,	900,	3770,	80840000,	2080,	12,	Manufacturer("Mitsubishi",	countryCode="JP"),	1740,	imageUrl =""),
Model("Pilatus PC-12",	"Pilatus",	9,	9,	5,	8,	528,	3417,	1786000,	1456,	0,	Manufacturer("Pilatus",	countryCode="CH"),	748,	imageUrl =""),
Model("Saab 2000",	"Saab Regional",	58,	58,	5,	88,	608,	2868,	25944000,	1820,	3,	Manufacturer("Saab",	countryCode="SE"),	1252,	imageUrl =""),
Model("Saab 90 Scandia",	"Saab Regional",	32,	32,	6,	36,	340,	2650,	6768000,	1820,	0,	Manufacturer("Saab",	countryCode="SE"),	850,	imageUrl =""),
Model("Shaanxi Y-10",	"Shaanxi Y",	178,	178,	4,	755,	917,	5560,	38258000,	1040,	12,	Manufacturer("Shaanxi Aircraft Corporation",	countryCode="CN"),	2700,	imageUrl =""),
Model("Sikorsky S-76",	"Sikorsky",	12,	12,	7,	17,	287,	761,	3384000,	1560,	3,	Manufacturer("Sikorsky",	countryCode="US"),	1,	imageUrl =""),
Model("Sikorsky S-92",	"Sikorsky",	25,	19,	8,	35,	290,	998,	9024000,	1560,	3,	Manufacturer("Sikorsky",	countryCode="US"),	1,	imageUrl =""),
Model("Sukhoi KR860",	"Sukhoi",	860,	860,	5,	4770,	907,	9400,	308508000,	1300,	87,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	3400,	imageUrl =""),
Model("Sukhoi Su-80",	"Sukhoi",	32,	32,	5,	44,	520,	1600,	6204000,	1300,	12,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	650,	imageUrl =""),
Model("Sukhoi Superjet 100",	"Sukhoi",	108,	108,	6,	300,	828,	4578,	52076000,	1560,	24,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	1731,	imageUrl ="https://www.norebbo.com/2016/02/sukhoi-ssj-100-blank-illustration-templates/"),
Model("Sukhoi Superjet 130NG",	"Sukhoi",	130,	130,	6,	499,	871,	4008,	58186000,	1560,	24,	Manufacturer("JSC Sukhoi",	countryCode="RU"),	1731,	imageUrl =""),
Model("Tupolev Tu-124",	"Tupolev Tu",	56,	56,	1,	185,	970,	2300,	8554000,	1300,	12,	Manufacturer("Tupolev",	countryCode="RU"),	1550,	imageUrl =""),
Model("Tupolev Tu-204",	"Tupolev Tu",	210,	210,	4,	965,	810,	4300,	39856000,	1300,	24,	Manufacturer("Tupolev",	countryCode="RU"),	1870,	imageUrl ="https://www.norebbo.com/tupolev-tu-204-100-blank-illustration-templates/"),
Model("Tupolev Tu-334-100",	"Tupolev Tu",	102,	102,	4,	288,	820,	4100,	30644000,	1300,	12,	Manufacturer("Tupolev",	countryCode="RU"),	1940,	imageUrl =""),
Model("Tupolev Tu-334-200",	"Tupolev Tu",	126,	126,	4,	465,	820,	3150,	33558000,	1300,	6,	Manufacturer("Tupolev",	countryCode="RU"),	1820,	imageUrl =""),
Model("Vickers VC10",	"Vickers",	150,	150,	3,	565,	930,	9410,	59032000,	1560,	18,	Manufacturer("Vickers-Armstrongs",	countryCode="GB"),	2520,	imageUrl =""),
Model("Xi'an MA600",	"Xi'an Turboprops",	60,	60,	4,	99,	514,	1600,	22090000,	1300,	6,	Manufacturer("Xi'an Aircraft Industrial Corporation",	countryCode="CN"),	750,	imageUrl =""),
Model("Xi'an MA700",	"Xi'an Turboprops",	86,	86,	4,	180,	637,	1500,	33840000,	1300,	12,	Manufacturer("Xi'an Aircraft Industrial Corporation",	countryCode="CN"),	630,	imageUrl =""),
Model("Yakovlev MC-21-100",	"Yakovlev MC-21",	132,	132,	5,	455,	870,	6140,	52734000,	1300,	30,	Manufacturer("Irkut",	countryCode="RU"),	1322,	imageUrl =""),
Model("Yakovlev MC-21-200",	"Yakovlev MC-21",	165,	165,	5,	615,	870,	6400,	68056000,	1300,	36,	Manufacturer("Irkut",	countryCode="RU"),	1350,	imageUrl =""),
Model("Yakovlev MC-21-300",	"Yakovlev MC-21",	211,	211,	5,	865,	870,	6000,	79054000,	1300,	45,	Manufacturer("Irkut",	countryCode="RU"),	1644,	imageUrl ="https://www.norebbo.com/irkut-mc-21-300/"),
Model("Yakovlev MC-21-400",	"Yakovlev MC-21",	230,	230,	5,	937,	870,	5500,	93906000,	1300,	54,	Manufacturer("Irkut",	countryCode="RU"),	1500,	imageUrl =""),
Model("Zeppelin",	"Zeppelin",	432,	72,	10,	22,	148,	8000,	121918000,	624,	24,	Manufacturer("Zeppelin Luftschifftechnik GmbH",	countryCode="DE"),	100,	imageUrl =""),
Model("Zeppelin NT",	"Zeppelin",	50,	20,	7,	3,	138,	900,	10246000,	936,	18,	Manufacturer("Zeppelin Luftschifftechnik GmbH",	countryCode="DE"),	100,	imageUrl =""),
  )
  val modelByName = models.map { model => (model.name, model) }.toMap
}
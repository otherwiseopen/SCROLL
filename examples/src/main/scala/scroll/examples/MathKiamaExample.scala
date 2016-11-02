package scroll.examples

import scroll.internal.Compartment
import scroll.internal.support.DispatchQuery
import DispatchQuery._
import scroll.internal.util.Log.info
import org.bitbucket.inkytonik.kiama.attribution.Attribution
import org.bitbucket.inkytonik.kiama.parsing.{Failure, Parsers, Success}
import org.bitbucket.inkytonik.kiama.util.{Positions, StringSource}
import org.bitbucket.inkytonik.kiama.rewriting.Rewriter

object MathKiamaExample extends App with Compartment {

  sealed abstract class Exp

  case class Num(i: Double) extends Exp

  case class Add(l: Exp, r: Exp) extends Exp

  case class Sub(l: Exp, r: Exp) extends Exp

  case class Mul(l: Exp, r: Exp) extends Exp

  case class Div(l: Exp, r: Exp) extends Exp

  case class SimpleMath() extends Attribution {
    val value: Exp => Double =
      attr {
        case Num(i) => i
        case Add(l, r) => value(l) + value(r)
        case Sub(l, r) => value(l) - value(r)
        case Mul(l, r) => value(l) * value(r)
        case Div(l, r) => value(l) / value(r)
      }
  }

  case class Optimizer() extends Rewriter {

    def optimise(e: Exp): Exp = rewrite(optimiser)(e)

    lazy val optimiser = bottomup(attempt(simplifier))

    lazy val simplifier =
      rule[Exp] {
        case Add(Num(0), e) => e
        case Add(e, Num(0)) => e
        case Sub(Num(0), e) => e
        case Sub(e, Num(0)) => e
        case Mul(Num(1), e) => e
        case Mul(e, Num(1)) => e
        case Mul(z@Num(0), _) => z
        case Mul(_, z@Num(0)) => z
        case Div(e, Num(1)) => e
        case Div(_, Num(0)) => throw new IllegalArgumentException("Division by 0!")
      }
  }

  case class Parser() extends Parsers(new Positions) {

    def parse(in: String): Exp = parse(expr, new StringSource(in)) match {
      case Success(e, _) => e
      case Failure(m, _) => throw new RuntimeException("Parsing failed: " + m)
    }

    lazy val expr: Parser[Exp] = term ~ rep("[+-]".r ~ term) ^^ {
      case t ~ ts => ts.foldLeft(t) {
        case (t1, "+" ~ t2) => Add(t1, t2)
        case (t1, "-" ~ t2) => Sub(t1, t2)
      }
    }

    lazy val term = factor ~ rep("[*/]".r ~ factor) ^^ {
      case t ~ ts => ts.foldLeft(t) {
        case (t1, "*" ~ t2) => Mul(t1, t2)
        case (t1, "/" ~ t2) => Div(t1, t2)
      }
    }

    lazy val factor = "(" ~> expr <~ ")" | num

    lazy val num = regex("[0-9]+".r) ^^ (s => Num(s.toDouble))

  }

  case class LoggerRole() extends Attribution {
    val value: Exp => Double = attr {
      case exp: Exp =>
        info("Evaluating: " + exp)
        implicit val dd = From(_.isInstanceOf[SimpleMath]).
          To(_.isInstanceOf[LoggerRole]).
          Through(anything).
          Bypassing(_.isInstanceOf[LoggerRole])
        val rFunc: Exp => Double = (+this).value
        rFunc(exp)
    }
  }

  // make it run
  val someMath = SimpleMath() play Optimizer() play Parser() play LoggerRole()

  val ast: Exp = someMath.parse("1+2+3*0")
  info("AST: " + ast)

  val optimizedAst: Exp = someMath.optimise(ast)
  info("optimized AST: " + optimizedAst)

  val resultFunc: Exp => Double = someMath.value
  val result = resultFunc(optimizedAst)
  info("Result: " + result)

  assert(3 == result)
}
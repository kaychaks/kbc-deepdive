#! /usr/bin/env amm

import java.nio.charset.CodingErrorAction
import scala.io.Codec

sealed trait Party

final case object Agent extends Party

final case object User extends Party

case class Name(value: String) extends AnyVal

case class Text(value: String) extends AnyVal

case class TurnID(value: Int) extends AnyVal

object Party {
       def apply(s: Option[String]): Option[Party] =
       s.fold[Option[Party]](None)(str =>
       if (str.toLowerCase == "agent") Some(Agent) else Some(User))
}

sealed trait TurnData
final case class Turn(p: Party,
                      n: Name,
                      t: Text,
                      tId: TurnID,
                      replyTo: TurnID)
      extends TurnData

val reg = """(?=\[(Agent|User)\s*\(\s*(.*?)\)\s*\]#\s*(.*?)\s*(?:\[|$))""".r

// main function
@main
def parse() = {

    val codec = scala.io.Codec.UTF8
             .onUnmappableCharacter(CodingErrorAction.REPLACE)
             .onMalformedInput(CodingErrorAction.IGNORE)
    val lineIter = scala.io.Source.fromInputStream(System.in)(codec)

    lineIter.getLines foreach {line =>
      val tabs = line.split("\t")
      val id = tabs.head
      val str = tabs(1)

      val ms = for(m <- reg.findAllMatchIn(str)) yield (Option(m.group(1)), Option(m.group(2)), Option(m.group(3)))

      val ls = ms.toList.foldLeft((List[TurnData](),0)){(acc, m) =>
          val l = m match {
              case(agentOrUser, name, text) => for {
                            au <- Party(agentOrUser)
                            n <- name
                            t <- text
              } yield Turn(au, Name(n), Text(t), TurnID(acc._2 + 1),TurnID(acc._2))
          }
          (l.map(_ :: acc._1).getOrElse(acc._1), acc._2 + 1)
      }

      ls._1.reverse.foreach {
                        case t: Turn =>
                             Console.println(
                              s"${id}\t${t.p.toString}\t${t.n.value}\t${t.t.value}\t${t.tId.value}\t${t.replyTo.value}"
                             )
      }
    }    
}
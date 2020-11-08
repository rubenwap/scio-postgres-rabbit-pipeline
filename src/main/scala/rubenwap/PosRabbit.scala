package rubenwap

import java.sql.ResultSet
import java.time._

import com.rabbitmq.client.ConnectionFactory
import com.spotify.scio._
import com.spotify.scio.coders.{Coder, CoderMaterializer}
import org.apache.beam.sdk.io.jdbc.JdbcIO

case class Movie(
                   cinema: String,
                   details: String,
                   title: String,
                   datetime: LocalDate
                 )

object PosRabbit {
  
  def main(cmdlineArgs: Array[String]): Unit = {
    
    val (sc, _) = ContextAndArgs(cmdlineArgs)
   
    val db = JdbcIO.DataSourceConfiguration
      .create("org.postgresql.Driver", "jdbc:postgresql://ec2-23-21-115-109.compute-1.amazonaws.com/d5uruq2ffeev6a")
      .withUsername("jfkhimgzvavcmg")
      .withPassword("e21ba33872db1d0f6d3e381e985f307d43f49414f95e0954f931c7cc11ec07c4")
    
    val readFromDb = JdbcIO
      .read[Movie]()
      .withCoder(CoderMaterializer.beam(sc, Coder[Movie]))
      .withDataSourceConfiguration(db)
      .withQuery("SELECT * FROM movies WHERE datetime is not null")
      .withRowMapper(new JdbcIO.RowMapper[Movie] {
        override def mapRow(resultSet: ResultSet): Movie = {
          val cinema = resultSet.getString("cinema")
          val title = resultSet.getString("title")
          val details = resultSet.getString("details")
          val dt = resultSet.getDate("datetime").toLocalDate
          Movie(cinema,details,title,dt)
        }
      })
    
    def writeToRabbit(e:Movie) = {
      val factory = new ConnectionFactory()
      factory.setUri("amqp://guest:guest@localhost:5672")
      val conn = factory.newConnection()
      val channel = conn.createChannel
      channel.exchangeDeclare("test", "direct", true)
      channel.queueDeclare("movies", true, false, false, null)
      channel.queueBind("movies", "test", "movies")
      channel.basicPublish("test", "movies", null, e.toString.getBytes())
    }
    
    sc.customInput("fromPostgres", readFromDb).map(e => writeToRabbit(e))
    sc.run().waitUntilDone()
    
  }
}

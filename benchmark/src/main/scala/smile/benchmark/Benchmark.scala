/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.benchmark

import smile.data._
import smile.data.parser.DelimitedTextParser
import smile.classification._
import smile.math.Math
import smile.math.distance.EuclideanDistance
import smile.math.kernel.GaussianKernel
import smile.math.rbf.GaussianRadialBasis
import smile.util.SmileUtils
import smile.validation.AUC

/**
 *
 * @author Haifeng Li
*/
object Benchmark {

  def main(args: Array[String]): Unit = {
    airline
    usps
  }

  def airline() {
    println("Airline")
    val parser = new DelimitedTextParser()
    parser.setDelimiter(",")
    parser.setColumnNames(true)
    parser.setResponseIndex(new NominalAttribute("class"), 8)
    val attributes = new Array[Attribute](8)
    attributes(0) = new NominalAttribute("V1")
    attributes(1) = new NominalAttribute("V2")
    attributes(2) = new NominalAttribute("V3")
    attributes(3) = new NumericAttribute("V4")
    attributes(4) = new NominalAttribute("V5")
    attributes(5) = new NominalAttribute("V6")
    attributes(6) = new NominalAttribute("V7")
    attributes(7) = new NumericAttribute("V8")

    val train = parser.parse("Benchmark train", attributes, "test-data/src/main/resources/smile/data/airline/train-0.1m.csv")
    val test = parser.parse("Benchmark test", attributes, "test-data/src/main/resources//smile/data/airline/test.csv")
    println("class: " + train.response.asInstanceOf[NominalAttribute].values.mkString(", "))

    val x = train.toArray(new Array[Array[Double]](train.size))
    val y = train.toArray(new Array[Int](train.size))
    val testx = test.toArray(new Array[Array[Double]](test.size))
    val testy = test.toArray(new Array[Int](test.size))

    // Random Forest
    var start = System.currentTimeMillis()
    val forest = new RandomForest(attributes, x, y, 500, 3)
    var end = System.currentTimeMillis()
    println("Random Forest 500 trees training time: %.2fs" format ((end-start)/1000.0))

    val posteriori = Array(0.0, 0.0)
    val prob = new Array[Double](testx.length)
    var error = (0 until testx.length).foldLeft(0) { (e, i) =>
      val yi = forest.predict(testx(i), posteriori)
      prob(i) = posteriori(1)
      if (yi != testy(i)) e + 1 else e
    }

    var auc = 100.0 * new AUC().measure(testy, prob)
    println("Random Forest OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Random Forest error rate = %.2f%%" format (100.0 * error / testx.length))
    println("Random Forest AUC = %.2f%%" format auc)

    // Gradient Tree Boost
    start = System.currentTimeMillis()
    val boost = new GradientTreeBoost(attributes, x, y, 100, 1000, 0.01, 0.7)
    end = System.currentTimeMillis()
    println("Gradient Tree Boost 100 trees training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      val yi = boost.predict(testx(i), posteriori)
      prob(i) = posteriori(1)
      if (yi != testy(i)) e + 1 else e
    }

    auc = 100.0 * new AUC().measure(testy, prob)
    println("Gradient Tree Boost error rate = %.2f%%" format (100.0 * error / testx.length))
    println("Gradient Tree Boost AUC = %.2f%%" format auc)
  }

  def usps() {
    println("USPS")
    val parser = new DelimitedTextParser
    parser.setResponseIndex(new NominalAttribute("class"), 0)

    val train = parser.parse("USPS Train", this.getClass.getResourceAsStream("/smile/data/usps/zip.train"))
    val test = parser.parse("USPS Test", this.getClass.getResourceAsStream("/smile/data/usps/zip.test"))
    val x = train.toArray(new Array[Array[Double]](train.size))
    val y = train.toArray(new Array[Int](train.size))
    val testx = test.toArray(new Array[Array[Double]](test.size))
    val testy = test.toArray(new Array[Int](test.size))
    val c = Math.max(y: _*) + 1

    var start = System.currentTimeMillis
    val forest = new RandomForest(x, y, 200)
    var end = System.currentTimeMillis
    println("Random Forest 200 trees training time: %.2fs" format ((end-start)/1000.0))

    var error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (forest.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("Random Forest OOB error rate = %.2f%%" format (100.0 * forest.error()))
    println("Random Forest error rate = %.2f%%" format (100.0 * error / testx.length))

    start = System.currentTimeMillis
    val svm = new SVM[Array[Double]](new GaussianKernel(8.0), 5.0, c, SVM.Multiclass.ONE_VS_ONE)
    svm.learn(x, y)
    svm.finish
    end = System.currentTimeMillis
    println("SVM one epoch training time: %.2fs" format ((end-start)/1000.0))
    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (svm.predict(testx(i)) != testy(i)) e + 1 else e
    }

    println("SVM error rate = %.2f%%" format (100.0 * error / testx.length))

    println("SVM one more epoch...")
    start = System.currentTimeMillis
    svm.learn(x, y)
    /*
    (0 until x.length) foreach { _ =>
      val j = Math.randomInt(x.length)
      svm.learn(x(j), y(j))
    }
*/
    svm.finish
    end = System.currentTimeMillis
    println("SVM one more epoch training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (svm.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("SVM error rate = %.2f%%" format (100.0 * error / testx.length))

    start = System.currentTimeMillis
    val centers = new Array[Array[Double]](200)
    val basis = SmileUtils.learnGaussianRadialBasis(x, centers)
    val rbf = new RBFNetwork[Array[Double]](x, y, new EuclideanDistance, new GaussianRadialBasis(8.0), centers)
    end = System.currentTimeMillis
    println("RBF 200 centers training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (rbf.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("RBF error rate = %.2f%%" format (100.0 * error / testx.length))

    start = System.currentTimeMillis
    val logit = new LogisticRegression(x, y, 0.3, 1E-3, 1000)
    end = System.currentTimeMillis
    println("Logistic regression training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (logit.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("Logistic error rate = %.2f%%" format (100.0 * error / testx.length))

    val p = x(0).length
    val mu = Math.colMean(x)
    val sd = Math.colSd(x)
    x.foreach { xi =>
      (0 until p) foreach { j => xi(j) = (xi(j) - mu(j)) / sd(j)}
    }
    testx.foreach { xi =>
      (0 until p) foreach { j => xi(j) = (xi(j) - mu(j)) / sd(j)}
    }

    start = System.currentTimeMillis
    val nnet = new NeuralNetwork(NeuralNetwork.ErrorFunction.LEAST_MEAN_SQUARES, NeuralNetwork.ActivationFunction.LOGISTIC_SIGMOID, p, 40, c)
    (0 until 30) foreach { _ => nnet.learn(x, y) }
    end = System.currentTimeMillis
    println("Neural Network 30 epoch training time: %.2fs" format ((end-start)/1000.0))

    error = (0 until testx.length).foldLeft(0) { (e, i) =>
      if (nnet.predict(testx(i)) != testy(i)) e + 1 else e
    }
    println("Neural Network error rate = %.2f%%" format (100.0 * error / testx.length))
  }
}
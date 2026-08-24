// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
    apiKey: "AIzaSyCd4mng6e3vTapDbpDe91Q6WnYF6w8qn6A",
    authDomain: "svartmusiccenter.firebaseapp.com",
    projectId: "svartmusiccenter",
    storageBucket: "svartmusiccenter.firebasestorage.app",
    messagingSenderId: "387525302015",
    appId: "1:387525302015:web:b1ef490f86c7a46fd85eca",
    measurementId: "G-39GL57LYNF"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
const analytics = getAnalytics(app);
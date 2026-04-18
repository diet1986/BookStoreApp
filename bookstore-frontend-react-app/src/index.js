import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import store from './store';
import './bootstrap.min.css';
import './index.css';
import reportWebVitals from './reportWebVitals';
import { Provider } from 'react-redux';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <Provider store={store}>
    <App />
  </Provider>
);

reportWebVitals();
